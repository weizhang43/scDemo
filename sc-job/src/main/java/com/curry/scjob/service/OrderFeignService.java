package com.curry.scjob.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import response.ResponseDto;

@Component
@FeignClient(value = "sc-order", path = "/sc-order")
public interface OrderFeignService {

    /**
     * 触发未提交订单超时取消任务，返回本次扫描的未提交订单数量。
     */
    @PostMapping("/order/job/handleUnSubmitOrder")
    ResponseDto<Integer> handleUnSubmitOrder();

    /**
     * 触发发货超时自动确认收货任务，返回本次扫描到的订单数量。
     */
    @PostMapping("/order/job/autoConfirmReceive")
    ResponseDto<Integer> autoConfirmReceive();

    /**
     * 触发售后退款重试任务，返回本次处理的工单数量。
     */
    @PostMapping("/order/job/retryAfterSaleRefund")
    ResponseDto<Integer> retryAfterSaleRefund();
}
