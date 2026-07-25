package com.example.scorder;

import com.curry.model.Order;
import com.example.scorder.config.RabbitMqConfig;
import com.example.scorder.listener.OrderMqListener;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RabbitMqTest {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderMqListener orderMqListener;

    /**
     * 先发送，再由测试主动调用消费方法拉取，验证发送与消费链路。
     */
    @Test
    public void testSendThenConsume() {
        /*String received = orderMqConsumer.receiveOrder();
        System.out.println("=========="+received);*/
    }

    @Test
    public void sendMsg() {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_DIRECT,
                RabbitMqConfig.ROUTING_KEY_ORDER,
                "订单已创建，订单号为001"
        );
    }

    @Test
    public void sendObject() {
        Order order = new Order();
        order.setOrderNo("001");
        order.setAddPerson("zhangwei");
        order.setOrderAddress("安徽和县");
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_TOPIC,
                "order.create.success",
                order
        );
    }

    @Test
    public void sendDelayMessage() {
        String message = "hello mq";
        int delayMillis = 10000;
        rabbitTemplate.convertAndSend(
                "exchange.delay",
                "routing.delay",
                message,
                msg -> {
                    msg.getMessageProperties().setDelay(delayMillis);
                    return msg;
                }
        );
    }



}
