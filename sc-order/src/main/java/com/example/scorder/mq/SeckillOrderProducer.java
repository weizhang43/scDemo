package com.example.scorder.mq;

import com.example.scorder.dto.SeckillRequest;

/**
 * 秒杀订单消息生产者。
 * 当前用 Redisson 阻塞队列实现（MQ 占位）；后续接 RabbitMQ/RocketMQ 时只替换实现类，
 * 编排与业务代码不动。
 */
public interface SeckillOrderProducer {

    /**
     * 将秒杀下单消息投递到队列，由消费者异步落库。
     * @param message 秒杀请求（含 uId/pId/addressId/addPerson）
     */
    void send(SeckillRequest message);
}
