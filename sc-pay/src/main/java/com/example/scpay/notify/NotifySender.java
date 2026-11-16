package com.example.scpay.notify;

import com.curry.model.pay.PayNotifyMessage;
import com.curry.model.pay.PaySignUtil;
import com.example.scpay.config.MockPayProperties;
import com.example.scpay.entity.MockPayTxn;
import com.example.scpay.mapper.MockPayTxnMapper;
import com.example.scpay.mq.PayNotifyMqConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 支付通知 MQ 投递器：用 RocketMQ 替代原自建 ThreadPoolTaskScheduler 重试矩阵。
 *
 * 改造前（已删除）：
 *  - scheduleNotify → schedule(send, delayMs) → notifyScheduler.schedule(...)
 *  - send 内部：RestTemplate.postForObject 调商户 notifyUrl
 *  - 失败重试：手写 RETRY_DELAYS_MS = [5s, 15s, 60s] 三次递增
 *  - 双发支持：notifyDuplicate=true 时 500ms 后再发一次
 *
 * 改造后：
 *  - 仅负责构造消息并投递到 RocketMQ，不再直接调商户；
 *  - 延迟由 RocketMQ 延迟级别实现：首发 1s（对应原 notifyDelayMs=1000）；
 *  - 重试由 RocketMQ 消费者失败自动重试实现：消费方 NACK 后递增间隔重投；
 *  - 双发：用同一 transactionId 但不同 nonce 投两次，sc-order 侧 nonce 防重放会拒第二份，
 *    保持原有"演示商户侧幂等"语义（虽然拒了，但能验证幂等机制）。
 *
 * 商户回调动作的执行方：从本类移到了 sc-order 的 PayNotifyMqListener，
 * 因为 sc-order 本来就是支付回调的消费方，让它在 MQ 链路里继续做回调业务最自然。
 */
@Component
public class NotifySender {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotifySender.class);

    /** 回调结果落库的最大长度，超长截断 */
    private static final int RESULT_MAX_LEN = 60;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private MockPayProperties properties;

    @Autowired
    private MockPayTxnMapper txnMapper;

    @Value("${pay.secret}")
    private String paySecret;

    /**
     * 支付结果确定后投递 MQ 消息：首发延迟 1s，notifyDuplicate 打开时双发。
     *
     * 不再落 last_notify_result：原字段记录 HTTP 响应，MQ 链路下回调动作在 sc-order 执行，
     * sc-pay 不再知道响应内容。如需保留审计，可让 sc-order 通过反向 MQ 通知 sc-pay，
     * 但当前业务对 last_notify_result 的依赖不强（renotify 接口仅按 status 判断）。
     *
     * @param txn        交易单（已推进到终态）
     * @param tradeStatus 支付结果：SUCCESS / FAIL
     */
    public void scheduleNotify(MockPayTxn txn, String tradeStatus) {
        send(txn, tradeStatus, PayNotifyMqConst.DELAY_LEVEL_FIRST_SEND);
        if (properties.isNotifyDuplicate()) {
            // 双发：再投一条同 transactionId 不同 nonce 的消息。
            // 注意：原设计意图是演示商户侧幂等，sc-order nonce SETNX 会拒第二份，
            // 因此第二条消息消费时会落 fail（重复 nonce），但不会重复推进业务。
            send(txn, tradeStatus, PayNotifyMqConst.DELAY_LEVEL_FIRST_SEND);
        }
    }

    /**
     * 构造并投递一条支付通知消息到 RocketMQ。
     *
     * @param txn        交易单
     * @param tradeStatus 支付结果
     * @param delayLevel RocketMQ 延迟级别：1=1s（首发）；>1 时由消费端 NACK 触发的重试使用
     */
    private void send(MockPayTxn txn, String tradeStatus, int delayLevel) {
        String nonce = UUID.randomUUID().toString().replace("-", "");

        // 签名参数与原 HTTP 表单保持一致，sc-order 侧 PaySignUtil.verify 可直接复用
        Map<String, String> params = new HashMap<>();
        params.put("payNo", txn.getPayNo());
        params.put("transactionId", txn.getTransactionId());
        params.put("tradeStatus", tradeStatus);
        params.put("amount", txn.getAmount().toPlainString());
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        params.put("nonce", nonce);
        params.put("sign", PaySignUtil.sign(params, paySecret));

        PayNotifyMessage payload = new PayNotifyMessage(
                txn.getTransactionId(),
                txn.getPayNo(),
                txn.getAmount().toPlainString(),
                tradeStatus,
                nonce,
                params.get("sign")
        );

        Message<PayNotifyMessage> msg = MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, PayNotifyMqConst.payNotifyMsgKey(txn.getTransactionId()))
                .build();

        // syncSend 单条同步发送，broker 返回 SendResult 后才返回。
        // 不用 asyncSend：simulate 接口对延迟不敏感（本就是模拟支付），
        // 同步发送失败能立即抛异常被 MockPayServiceImpl 捕获，避免消息丢失静默。
        // delayLevel=1 表示首发延迟 1s，消费失败 NACK 时 broker 自动按 5s/10s/30s…重投。
        try {
            rocketMQTemplate.syncSend(PayNotifyMqConst.payNotifyDestination(), msg,
                    3000, delayLevel);
            // 落 notifyCnt 自增：保持原 t_mock_pay_txn.notify_cnt 字段的"通知次数"语义，
            // 便于运维查 broker 状态时对照业务侧计数
            txnMapper.recordNotifyResult(txn.getTransactionId(), "sent");
            LOGGER.info("[NotifySender] 投递成功 txn={}, tradeStatus={}, delayLevel={}",
                    txn.getTransactionId(), tradeStatus, delayLevel);
        } catch (Exception e) {
            // 投递失败：消息没到 broker，业务侧已推进到终态无法回滚（支付已完成）。
            // 此时只能记日志告警，依赖 renotify 接口由人工触发补偿。
            String errMsg = e.getClass().getSimpleName();
            String recorded = errMsg.length() > RESULT_MAX_LEN
                    ? errMsg.substring(0, RESULT_MAX_LEN) : errMsg;
            txnMapper.recordNotifyResult(txn.getTransactionId(), "send_failed:" + recorded);
            LOGGER.error("[NotifySender] 投递失败 txn={}, tradeStatus={}",
                    txn.getTransactionId(), tradeStatus, e);
        }
    }
}
