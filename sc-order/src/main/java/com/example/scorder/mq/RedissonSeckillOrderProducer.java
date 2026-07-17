package com.example.scorder.mq;

import com.example.scorder.dto.SeckillRequest;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 基于 Redisson 阻塞队列的秒杀订单生产者（MQ 占位实现）。
 */
@Component
public class RedissonSeckillOrderProducer implements SeckillOrderProducer {

    /** 秒杀订单队列 key（多节点共享，天然做消息分发） */
    public static final String QUEUE_KEY = "seckill:order:queue";

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public void send(SeckillRequest message) {
        RBlockingQueue<SeckillRequest> queue = redissonClient.getBlockingQueue(QUEUE_KEY);
        queue.add(message);
    }
}
