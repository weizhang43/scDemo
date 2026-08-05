package com.example.scorder.controller;

import com.example.scorder.service.AfterSaleService;
import com.example.scorder.service.OrderJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

/**
 * 定时任务触发端点：由 sc-job 的 XXL-Job 处理器通过 Feign 调用。
 */
@RestController
@RequestMapping("/order/job")
public class OrderJobController {

    @Autowired
    private OrderJobService orderJobService;

    @Autowired
    private AfterSaleService afterSaleService;

    /**
     * 扫描未提交订单，超时的异步取消。返回本次扫描的未提交订单数量。
     */
    @PostMapping("/handleUnSubmitOrder")
    public ResponseDto<Integer> handleUnSubmitOrder() {
        return ResponseDto.success(orderJobService.handleUnSubmitOrder());
    }

    /**
     * 扫描已发货且发货超过 N 天的订单，自动确认收货。返回本次扫描到的订单数量。
     */
    @PostMapping("/autoConfirmReceive")
    public ResponseDto<Integer> autoConfirmReceive() {
        return ResponseDto.success(orderJobService.autoConfirmReceive());
    }

    /**
     * 重试"同意退款中"但网关退款未成功的售后工单。返回本次处理数量。
     */
    @PostMapping("/retryAfterSaleRefund")
    public ResponseDto<Integer> retryAfterSaleRefund() {
        return ResponseDto.success(afterSaleService.retryRefund());
    }
}
