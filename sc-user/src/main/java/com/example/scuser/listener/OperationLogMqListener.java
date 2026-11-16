package com.example.scuser.listener;

import com.curry.log.mq.OperationLogMqConst;
import com.curry.model.OperationLog;
import com.example.scuser.service.OperationLogService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 操作日志 RabbitMQ 消费者：监听 queue.operation.log，落库到 t_operation_log。
 *
 * 与 sc-user 现有日志链路的关系：
 *  - sc-user 自身的 @OpLog 日志：经 DbOperationLogSink 直接落库（不经 MQ，避免自己绕远路）；
 *  - sc-product / sc-order / sc-pay 等其他模块的 @OpLog 日志：经 MQ 投递到本队列 → 本类消费落库。
 *
 * ACK 策略：
 *  - 落库成功 → basicAck；
 *  - 落库失败 → basicNack(requeue=false)，触发 retry 重试 3 次（由 application-mq.yml 配置），
 *    仍失败则死信到 dlx_queue（与 sc-order 订单超时取消共用死信路径）。
 *  - 操作日志非关键数据，重试 3 次仍失败可接受丢失，死信仅用于排障统计。
 *
 * 幂等：t_operation_log.log_id 自增主键，重复投递会产生多条记录（不破坏数据一致性）。
 * 当前不引入去重表，理由：操作日志允许偶发重复（审计场景多一条比少一条好）。
 */
@Component
public class OperationLogMqListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogMqListener.class);

    @Autowired
    private OperationLogService operationLogService;

    /**
     * 消费操作日志消息：调用 saveAsync 异步落库。
     *
     * 注意：saveAsync 自身是 @Async，本方法内调用会失效（同类调用绕过 AOP 代理）。
     * 但即便同步落库，RabbitListener 也是在自己的线程池里执行，不会阻塞 HTTP 请求线程。
     * 因此这里直接调用 saveAsync（即便是同步行为），影响只是落库耗时占住消费线程。
     *
     * @param log          操作日志实体（由 Jackson2JsonMessageConverter 从 JSON 反序列化）
     * @param channel      RabbitMQ Channel，用于手动 ACK
     * @param deliveryTag  消息投递标签
     */
    @RabbitListener(queues = OperationLogMqConst.QUEUE_OPERATION_LOG)
    public void onMessage(OperationLog log, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            operationLogService.saveAsync(log);
            channel.basicAck(deliveryTag, false);
            LOGGER.debug("[OperationLog-MQ] 落库完成 module={}, method={}",
                    log.getModule(), log.getMethod());
        } catch (Exception e) {
            LOGGER.error("[OperationLog-MQ] 落库失败 module={}, method={}",
                    log.getModule(), log.getMethod(), e);
            // NACK 不 requeue，触发 retry 重试 3 次（application-mq.yml 配置），
            // 仍失败则死信到 dlx_queue，与 sc-order 订单超时取消共用死信路径。
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
