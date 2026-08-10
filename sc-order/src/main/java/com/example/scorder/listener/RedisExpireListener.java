package com.example.scorder.listener;

import com.example.scorder.service.OrderService;
import org.redisson.api.RPatternTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Redis 键过期事件监听器：orderExpired:{oId} 键到期即触发订单超时取消
 * （cancelUnSubmitted 内部复查状态，已支付订单不会被误取消）。
 */
@Component
public class RedisExpireListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisExpireListener.class);

    /** 订单超时键前缀，键名为 前缀+订单ID */
    public static final String EXPIRED_KEY_PREFIX = "orderExpired:";

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private OrderService orderService;

    /**
     * 订阅所有 db 的键过期事件，命中订单超时键则触发取消。
     */
    @PostConstruct
    public void listen() {
        // Redis 键过期事件发布到 __keyevent@<db>__:expired，消息体是过期的 key 名。
        // 用 * 匹配所有 db，避免 db 号写死。
        RPatternTopic topic = redissonClient.getPatternTopic("__keyevent@*__:expired", StringCodec.INSTANCE);
        topic.addListener(String.class, (pattern, channel, expiredKey) -> {
            if (expiredKey != null && expiredKey.startsWith(EXPIRED_KEY_PREFIX)) {
                String orderId = expiredKey.substring(EXPIRED_KEY_PREFIX.length());
                Object result = orderService.cancelUnSubmitted(Integer.parseInt(orderId));
                LOGGER.info("订单超时未提交，自动取消 orderId={}, result={}", orderId, result);
            }
        });
    }
}
