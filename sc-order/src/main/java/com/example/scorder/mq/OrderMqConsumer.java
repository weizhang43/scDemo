package com.example.scorder.mq;

import com.example.scorder.config.RabbitMqConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单相关 RabbitMQ 消费者：采用主动拉取（pull）模式，
 * 由调用方显式调用 receiveOrder() 从队列取一条消息进行消费。
 */
@Slf4j
@Component
public class OrderMqConsumer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 从订单队列拉取一条消息（最多等待 3 秒）。
     * @return 消息内容；超时仍无消息时返回 null
     */
    public String receiveOrder() {
        Object msg = rabbitTemplate.receiveAndConvert(RabbitMqConfig.QUEUE_ORDER, 3000);
        if (msg == null) {
            log.info("[order-mq] 队列为空，无消息可消费");
            return null;
        }
        String message = msg.toString();
        log.info("[order-mq] 消费订单消息: {}", message);
        return message;
    }
}
