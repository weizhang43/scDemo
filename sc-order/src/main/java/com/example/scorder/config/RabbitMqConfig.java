package com.example.scorder.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Value("${order-timeout-minute:30}")
    private Integer orderTimeOutMinute;

    /**
     * JSON 消息转换器，替换默认 SimpleMessageConverter，支持发送/接收任意 POJO（如 Order）。
     * 容器中仅此一个 MessageConverter Bean 时，Spring Boot 会自动应用到 RabbitTemplate。
     */
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ========== 队列定义 ==========
    public static final String QUEUE_ORDER = "queue.order";
    public static final String QUEUE_EMAIL = "queue.email";

    // ========== 交换机定义 ==========
    public static final String EXCHANGE_DIRECT = "exchange.direct";
    public static final String EXCHANGE_TOPIC = "exchange.topic";
    public static final String EXCHANGE_FANOUT = "exchange.fanout";

    // ========== Routing Key ==========
    public static final String ROUTING_KEY_ORDER = "routing.order";
    public static final String ROUTING_KEY_EMAIL = "routing.email";

    /** topic 交换机的订单路由匹配模式：匹配 order 开头的多级路由键，如 order.create.success */
    public static final String TOPIC_PATTERN_ORDER = "order.#";


    // 死信队列
    public static final String DLX_EXCHANGE = "dlx_exchange";
    public static final String DLX_ROUTING_KEY = "dlx_routing_key";
    public static final String DLX_QUEUE = "dlx_queue";

    /**
     * 直连交换机
     * @return
     */
    @Bean
    public DirectExchange directExchange(){
        return new DirectExchange(EXCHANGE_DIRECT);
    }

    /**
     * topic交换机
     * @return
     */
    @Bean
    public TopicExchange topicExchange(){
        return new TopicExchange(EXCHANGE_TOPIC);
    }

    /**
     * 直连交换机
     * @return
     */
    @Bean
    public FanoutExchange fanoutExchange(){
        return new FanoutExchange(EXCHANGE_FANOUT);
    }

    /**
     * 订单队列
     * @return
     */
    @Bean
    public Queue orderQueue(){
        return QueueBuilder.durable(QUEUE_ORDER)
                .withArgument("x-message-ttl", orderTimeOutMinute * 60 * 1000) // 设置超时时间
                .withArgument("x-max-length",10000)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .build();
    }

    /**
     * 邮件队列
     * @return
     */
    @Bean
    public Queue emailQueue(){
        return QueueBuilder.durable(QUEUE_EMAIL).build();
    }


    /**
     * 直连交换机绑定订单队列
     * @return
     */
    @Bean
    public Binding orderBinding(){
        return BindingBuilder
                .bind(orderQueue())
                .to(directExchange())
                .with(ROUTING_KEY_ORDER);
    }

    /**
     * 直连交换机绑定邮件队列
     * @return
     */
    @Bean
    public Binding emailBinding(){
        return BindingBuilder
                .bind(emailQueue())
                .to(directExchange())
                .with(ROUTING_KEY_EMAIL);
    }

    /**
     * topic 交换机绑定订单队列：匹配 order.# 模式的路由键
     */
    @Bean
    public Binding orderTopicBinding(){
        return BindingBuilder
                .bind(orderQueue())
                .to(topicExchange())
                .with(TOPIC_PATTERN_ORDER);
    }



    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(DLX_ROUTING_KEY);
    }


}
