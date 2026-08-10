package com.example.scorder.listener;

import com.curry.model.OrderMessage;
import com.example.scorder.config.RabbitMqConfig;
import com.example.scorder.service.OrderService;
import com.example.scorder.service.UserFeignService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import response.ResponseDto;

import java.io.IOException;

/**
 * 订单相关 RabbitMQ 消费者：监听邮件队列，收到消息后通过 Feign 调用 sc-user 发送邮件。
 */
@Component
public class OrderMqListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderMqListener.class);

    @Autowired
    private UserFeignService userFeignService;

    @Autowired
    private OrderService orderService;

    /**
     * 消费订单状态变更邮件消息（acknowledge-mode: manual，必须手动 ACK，
     * 否则消息滞留 unacked，连接断开/重启后会被重投导致重复发邮件）。
     * 邮件属非关键通知：发送失败也 ACK（记日志），不 requeue，避免毒消息无限循环。
     */
    @RabbitListener(queues = RabbitMqConfig.QUEUE_EMAIL)
    public void receiveOrder(OrderMessage orderMessage, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            ResponseDto<?> result = userFeignService.sendMail(orderMessage);
            if (result != null && ResponseDto.SUCCESS_CODE.equals(result.getCode())) {
                LOGGER.info("邮件发送成功, toAcc={}, subject={}",
                        orderMessage.getToAcc(), orderMessage.getSubject());
            } else {
                LOGGER.error("邮件发送失败, toAcc={}, msg={}", orderMessage.getToAcc(),
                        result == null ? "响应为空" : result.getMsg());
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            LOGGER.error("邮件发送异常, toAcc={}", orderMessage.getToAcc(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 监听死信队列，处理订单超时（acknowledge-mode: manual，必须手动 ACK，
     * 否则消息滞留 unacked，连接断开/重启后会被重投导致重复取消订单）。
     * 取消失败也 ACK（记日志），不 requeue，避免毒消息无限循环。
     * 走 cancelUnSubmitted：顾客可能已在超时窗口内支付，那条延时消息不能再把订单取消掉。
     *
     * @param orderId 超时订单ID（消息体）
     */
    @RabbitListener(queues = RabbitMqConfig.DLX_QUEUE)
    public void handleTimeoutOrder(Integer orderId, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            LOGGER.info("订单超时取消，订单id: {}", orderId);
            Object result = orderService.cancelUnSubmitted(orderId);
            LOGGER.info("订单超时未提交，自动取消 orderId={}, result={}", orderId, result);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            LOGGER.error("订单超时取消异常, orderId={}", orderId, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
