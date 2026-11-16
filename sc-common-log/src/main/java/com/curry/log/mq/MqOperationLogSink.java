package com.curry.log.mq;

import com.curry.log.sink.OperationLogSink;
import com.curry.model.OperationLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * 基于 RabbitMQ 的操作日志出口：把日志投递到 sc-user 监听的队列。
 *
 * 与 FeignOperationLogSink 的关系：
 *  - 两者都实现 OperationLogSink 接口；
 *  - OperationLogAutoConfiguration 通过 @ConditionalOnProperty 决定加载哪个，
 *    默认走 MQ，可通过 op-log.sink=feign 切回旧 Feign 链路（保留兼容性）；
 *  - sc-user 自身的 DbOperationLogSink 仍然 @Primary 优先（直接落库，不绕 MQ）。
 *
 * 投递失败处理：
 *  - RabbitTemplate.convertAndSend 抛 AmqpException 时只记日志，不抛业务异常；
 *  - 切面已在异步线程池中调用本方法，吞掉异常不影响业务请求；
 *  - 日志丢失在可接受范围（非关键业务数据），重试由 RabbitMQ producer-confirm + retry 保证。
 *
 * 不在此处做消息确认回调（publisher-confirm 类型为 correlated 时可注册）：
 *  - 操作日志非关键数据，简单 fire-and-forget 足够；
 *  - 加回调会增加日志发送线程的阻塞，违背异步落地的初衷。
 */
public class MqOperationLogSink implements OperationLogSink {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqOperationLogSink.class);

    private final RabbitTemplate rabbitTemplate;

    public MqOperationLogSink(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 把操作日志投递到 RabbitMQ 队列 queue.operation.log。
     *
     * @param operationLog 待落地的操作日志
     */
    @Override
    public void save(OperationLog operationLog) {
        try {
            // RabbitTemplate 在容器中自动使用 Jackson2JsonMessageConverter（sc-user 已配置）；
            // 此处共享 starter 没单独配 converter，会用 SimpleMessageConverter，
            // 操作日志字段均为基础类型（String/Long/Integer/Date），可正常序列化。
            // sc-user 监听端按 OperationLog 类型反序列化。
            rabbitTemplate.convertAndSend(
                    OperationLogMqConst.EXCHANGE_OPERATION_LOG,
                    OperationLogMqConst.ROUTING_KEY_OPERATION_LOG,
                    operationLog
            );
        } catch (Exception e) {
            // 投递失败只记日志：切面调用方已在异步线程，吞异常不影响业务。
            // 日志丢失是可接受的（非关键数据），统计上由 LOGGER.error 反映失败率。
            LOGGER.error("[OperationLog] MQ 投递失败 module={} method={}",
                    operationLog.getModule(), operationLog.getMethod(), e);
        }
    }
}
