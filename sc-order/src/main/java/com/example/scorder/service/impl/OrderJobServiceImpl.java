package com.example.scorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.curry.model.Order;
import com.example.scorder.auth.OrderScope;
import com.example.scorder.config.OrderTimeoutProperties;
import com.example.scorder.service.OrderJobService;
import com.example.scorder.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.example.scorder.service.impl.OrderServiceImpl.COMPLETE_ORDER_STATUS;
import static com.example.scorder.service.impl.OrderServiceImpl.SHIPPED_ORDER_STATUS;
import static com.example.scorder.service.impl.OrderServiceImpl.UN_COMMIT_ORDER_STATUS;

/**
 * 未提交订单超时取消任务：由 sc-job 通过 Feign 触发（/order/job/handleUnSubmitOrder）。
 */
@Service
public class OrderJobServiceImpl implements OrderJobService {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderTimeoutProperties orderTimeoutProperties;

    @Autowired
    @Qualifier("changeStatusExecutor")
    private ThreadPoolTaskExecutor executor;

    /** 发货后超过该天数未确认收货则自动确认 */
    @Value("${order-auto-confirm-days:7}")
    private Integer autoConfirmDays;

    /**
     * 兜底扫描：MQ 延时取消漏掉的单（消息丢失、死信消费失败等）按 createTime 超时补取消。
     * 用当前 Nacos 配置值判断；cancelUnSubmitted 内部复查状态 + CAS，与 MQ 链路并发取消也幂等。
     */
    @Override
    public int handleUnSubmitOrder() {
        Date deadline = new Date(System.currentTimeMillis() - orderTimeoutProperties.getTimeoutMillis());
        List<Order> orderList = orderService.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderStatus, UN_COMMIT_ORDER_STATUS)
                .le(Order::getCreateTime, deadline));
        for (Order order : orderList) {
            CompletableFuture.runAsync(() -> orderService.cancelUnSubmitted(order.getOId()), executor);
        }
        return orderList.size();
    }

    /**
     * 自动确认收货：已发货(3)且发货时间超过配置天数的订单批量流转到已完成(2)。
     */
    @Override
    public int autoConfirmReceive() {
        Calendar deadline = Calendar.getInstance();
        deadline.add(Calendar.DAY_OF_MONTH, -autoConfirmDays);
        List<Order> orderList = orderService.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderStatus, SHIPPED_ORDER_STATUS)
                .le(Order::getShipTime, deadline.getTime()));
        for (Order order : orderList) {
            // updateStatus 内部 CAS(3→2)，与顾客手动确认并发时只有一方成功，另一方幂等返回
            CompletableFuture.runAsync(() -> orderService.updateStatus(
                    order.getOId(), COMPLETE_ORDER_STATUS, OrderScope.unrestricted()), executor);
        }
        return orderList.size();
    }
}
