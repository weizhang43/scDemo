package com.example.scorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.curry.model.Order;
import com.example.scorder.service.OrderJobService;
import com.example.scorder.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.example.scorder.listener.RedisExpireListener.EXPIRED_KEY_PREFIX;
import static com.example.scorder.service.impl.OrderServiceImpl.CANCEL_ORDER_STATUS;
import static com.example.scorder.service.impl.OrderServiceImpl.UN_COMMIT_ORDER_STATUS;

/**
 * 未提交订单超时取消任务：由 sc-job 通过 Feign 触发（/order/job/handleUnSubmitOrder）。
 */
@Slf4j
@Service
public class OrderJobServiceImpl implements OrderJobService {

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    @Qualifier("changeStatusExecutor")
    private ThreadPoolTaskExecutor executor;

    @Override
    public int handleUnSubmitOrder() {
        List<Order> orderList = orderService.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderStatus, UN_COMMIT_ORDER_STATUS));
        for (Order order : orderList) {
            CompletableFuture.runAsync(() -> dealUnSubmitOrder(order), executor);
        }
        return orderList.size();
    }

    /**
     * Redis 超时 key 已失效说明订单超时未提交，取消订单。
     */
    private void dealUnSubmitOrder(Order order) {
        String key = EXPIRED_KEY_PREFIX + order.getOId();
        if (!redisTemplate.hasKey(key)) {
            orderService.updateStatus(order.getOId(), CANCEL_ORDER_STATUS);
        }
    }
}
