package com.example.scorder.mq;

import com.example.scorder.dto.SeckillRequest;

/**
 * 秒杀订单消息生产者。
 * 当前用 Redisson 阻塞队列实现（MQ 占位）；后续接 RabbitMQ/RocketMQ 时只替换实现类，
 * 编排与业务代码不动。
 */
public interface SeckillOrderProducer {

    void send(SeckillRequest message);
}
