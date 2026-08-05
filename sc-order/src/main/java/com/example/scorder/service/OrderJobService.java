package com.example.scorder.service;

public interface OrderJobService {

    /**
     * 扫描未提交订单，Redis 超时 key 已失效的取消订单。
     * @return 本次扫描的未提交订单数量
     */
    int handleUnSubmitOrder();

    /**
     * 扫描已发货(3)且发货超过 N 天的订单，自动确认收货(3→2)。
     * @return 本次扫描到的超时未收货订单数量
     */
    int autoConfirmReceive();
}
