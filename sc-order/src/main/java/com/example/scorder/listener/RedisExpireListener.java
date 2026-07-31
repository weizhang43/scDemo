package com.example.scorder.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.curry.model.Order;
import com.example.scorder.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RPatternTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Slf4j
public class RedisExpireListener {

    public static final String EXPIRED_KEY_PREFIX = "orderExpired:";

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private OrderService orderService;

    @PostConstruct
    public void listen() {
        // Redis 键过期事件发布到 __keyevent@<db>__:expired，消息体是过期的 key 名。
        // 用 * 匹配所有 db，避免 db 号写死。
        RPatternTopic topic = redissonClient.getPatternTopic("__keyevent@*__:expired", StringCodec.INSTANCE);
        topic.addListener(String.class, (pattern, channel, expiredKey) -> {
            if (expiredKey != null && expiredKey.startsWith(EXPIRED_KEY_PREFIX)) {
                String orderId = expiredKey.substring(EXPIRED_KEY_PREFIX.length());
                Object result = orderService.cancelUnSubmitted(Integer.parseInt(orderId));
                log.info("订单超时未提交，自动取消 orderId={}, result={}",orderId,result);
            }
        });
    }
}
