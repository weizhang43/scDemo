package com.example.scorder.mq;

import com.example.scorder.dto.SeckillRequest;
import com.example.scorder.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单消费者（MQ 占位实现）：后台守护线程持续从 Redisson 队列取消息并异步落库。
 * 多节点部署时各节点各起一个消费者，队列天然分发，不会重复消费同一条消息。
 */
@Slf4j
@Component
public class SeckillOrderConsumer {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private OrderService orderService;

    private volatile boolean running = true;
    private Thread worker;

    /**
     * 容器初始化后启动后台守护线程循环消费秒杀队列。
     */
    @PostConstruct
    public void start() {
        worker = new Thread(this::loop, "seckill-order-consumer");
        worker.setDaemon(true);
        worker.start();
        log.info("[seckill-consumer] started");
    }

    /**
     * 消费循环：从 Redisson 阻塞队列 poll 消息，单条异常不影响后续消费，补偿逻辑在 Service 内处理。
     */
    private void loop() {
        RBlockingQueue<SeckillRequest> queue =
                redissonClient.getBlockingQueue(RedissonSeckillOrderProducer.QUEUE_KEY);
        while (running) {
            try {
                SeckillRequest msg = queue.poll(2, TimeUnit.SECONDS);
                if (msg != null) {
                    orderService.processSeckillOrder(msg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // 单条消息异常不影响后续消费；补偿在 processSeckillOrder 内部完成
                log.error("[seckill-consumer] process message error", e);
            }
        }
        log.info("[seckill-consumer] stopped");
    }

    /**
     * 容器销毁前停止消费循环：翻转运行标志并中断工作线程。
     */
    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }
}
