package com.curry.log.mq;

/**
 * 操作日志 RabbitMQ 常量：与 sc-user 侧监听器约定一致。
 *
 * 设计原则：
 *  - 共享 starter sc-common-log 是生产者，sc-user 是消费者；
 *  - 共享同一组 Topic/Queue/RoutingKey 字面量，通过 code review 保证一致；
 *  - 不放到 sc-model 共享，因为 sc-model 是数据模型层，不应承载 MQ 协议常量。
 *
 * 选用 RabbitMQ 而非 RocketMQ 的理由：
 *  - 操作日志是典型"可丢失但不应丢"的弱可靠场景，RabbitMQ 的持久化 + ACK 足够；
 *  - 系统已有 RabbitMQ（sc-order 在用），共享 starter 引入不增加新 broker；
 *  - 日志无顺序、无延迟、无削峰需求，RocketMQ 的吞吐优势用不上。
 */
public final class OperationLogMqConst {

    private OperationLogMqConst() {
    }

    /** 操作日志交换机：用 direct 类型，简单路由 */
    public static final String EXCHANGE_OPERATION_LOG = "exchange.operation.log";

    /** 操作日志队列：sc-user 监听消费 */
    public static final String QUEUE_OPERATION_LOG = "queue.operation.log";

    /** 路由 key */
    public static final String ROUTING_KEY_OPERATION_LOG = "routing.operation.log";
}
