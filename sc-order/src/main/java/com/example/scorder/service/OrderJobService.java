package com.example.scorder.service;

public interface OrderJobService {

    /**
     * 扫描未提交订单，Redis 超时 key 已失效的取消订单。
     * @return 本次扫描的未提交订单数量
     */
    int handleUnSubmitOrder();
}
