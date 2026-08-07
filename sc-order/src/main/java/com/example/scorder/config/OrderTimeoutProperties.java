package com.example.scorder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 订单超时时长配置。@RefreshScope 使 Nacos 热改 order-timeout-minute 后新下单立即生效；
 * 超时通过消息级 TTL 传递（而非队列 x-message-ttl），已发出的延时消息保持下单时的超时值。
 */
@Component
@RefreshScope
public class OrderTimeoutProperties {

    @Value("${order-timeout-minute:30}")
    private Integer timeoutMinute;

    public int getTimeoutMinute() {
        return timeoutMinute;
    }

    public long getTimeoutMillis() {
        return timeoutMinute * 60L * 1000L;
    }
}
