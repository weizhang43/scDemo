package com.example.scorder.mq;

import com.curry.model.pay.PayNotifyMessage;
import com.example.scorder.service.PayService;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付通知消息消费者：监听 RocketMQ 的 pay_notify_topic，收到消息后调用 PayService.handleNotify。
 *
 * 改造前：sc-pay 的 NotifySender 用 RestTemplate 直接 HTTP 调 sc-order 的 /order/pay/notify；
 * 改造后：sc-pay 投递 MQ 消息，本类消费后调用同一 handleNotify 方法，业务逻辑零改动。
 *
 * 监听参数说明：
 *  - topic = pay_notify_topic：与 sc-pay 约定一致
 *  - selectorExpression = PAY_RESULT：订阅 Tag 过滤
 *  - consumerGroup = pay-notify-consumer-group：集群模式，多实例分摊消费不重复
 *  - consumeMode = CONCURRENTLY：并发消费，支付通知无顺序要求
 *    （同一支付单的状态机靠 PayRecord CAS 保证，不依赖消息顺序）
 *  - messageModel = CLUSTERING：集群模式
 *  - maxReconsumeTimes = 5：消费失败最多重试 5 次
 *    覆盖原 sc-pay [5s,15s,60s] 三次重试 + RocketMQ 默认 1s/5s/10s/30s/1m…递增
 *
 * 重试语义说明：
 *  - onMessage 抛异常 → RocketMQ 自动按 1s/5s/10s/30s/1m…递增重试，最多 5 次；
 *  - 重试 5 次仍失败 → 消息进入死信队列 %DLQ%pay-notify-consumer-group，
 *    后续可加专门监听器做告警（当前先依赖 RocketMQ Dashboard 排查）。
 *
 * 业务幂等：handleNotify 内部已有 nonce SETNX 防重放 + PayRecord CAS 状态机，
 * 即便消息重复投递也不会重复推进业务，无需额外幂等守卫。
 */
@Component
@RocketMQMessageListener(
        topic = PayNotifyMqConst.TOPIC_PAY_NOTIFY,
        selectorExpression = PayNotifyMqConst.TAG_PAY_RESULT,
        consumerGroup = PayNotifyMqConst.CONSUMER_GROUP_PAY_NOTIFY,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = PayNotifyMqConst.MAX_RECONSUME_TIMES
)
public class PayNotifyMqListener implements RocketMQListener<PayNotifyMessage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayNotifyMqListener.class);

    @Autowired
    private PayService payService;

    /**
     * 消费消息：把 PayNotifyMessage 还原成原 HTTP 表单参数 Map，调用 handleNotify。
     *
     * @param message 支付通知消息体（由 RocketMQ Spring 客户端从 JSON 反序列化得到）
     */
    @Override
    public void onMessage(PayNotifyMessage message) {
        LOGGER.info("[PayNotify-MQ] 收到消息 txn={}, payNo={}, tradeStatus={}",
                message.getTransactionId(), message.getPayNo(), message.getTradeStatus());

        // 还原为 handleNotify 期望的 params Map：
        //  - 字段名与原 HTTP 表单完全一致，PaySignUtil.verify 与 doHandleNotify 无需改动；
        //  - timestamp 用当前时间：原 timestamp 用于 HTTP 链路防过期，
        //    MQ 链路 nonce SETNX 已防重放，timestamp 仅满足 validateTimestamp 的存在性校验。
        Map<String, String> params = new HashMap<>();
        params.put("payNo", message.getPayNo());
        params.put("transactionId", message.getTransactionId());
        params.put("tradeStatus", message.getTradeStatus());
        params.put("amount", message.getAmount());
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        params.put("nonce", message.getNonce());
        params.put("sign", message.getSign());

        // 调用现有 handleNotify：内部完成验签 → nonce SETNX → 金额核对 → CAS 推进支付单与订单。
        // 返回 "success" 表示无需重试，"fail" 表示需要重试。
        // 注意：onMessage 抛异常会触发 RocketMQ 重试，但 handleNotify 内部已 catch 业务异常
        // 并返回 "fail" 不抛出，因此这里需要根据返回值决定是否抛异常。
        String result = payService.handleNotify(params);
        if (!"success".equals(result)) {
            // 业务校验失败（如 nonce 重复、金额不符）：抛异常触发重试。
            // 重试时 nonce 仍是同一条消息携带的（不会换），nonce SETNX 会持续拒收 →
            // 重试 maxReconsumeTimes 次后进死信，由人工介入排障。
            // 这是预期行为：nonce 重复说明上游已处理过该消息，不该再处理。
            LOGGER.warn("[PayNotify-MQ] 业务处理失败 txn={}, result={}，触发重试",
                    message.getTransactionId(), result);
            throw new RuntimeException("pay notify handle failed: " + result);
        }
        LOGGER.info("[PayNotify-MQ] 消费完成 txn={}", message.getTransactionId());
    }
}
