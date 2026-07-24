package com.example.scorder.controller;

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

    /**
     * 扫描未提交订单，超时的异步取消。返回本次扫描的未提交订单数量。
     */
    @PostMapping("/handleUnSubmitOrder")
    public ResponseDto<Integer> handleUnSubmitOrder() {
        return ResponseDto.success(orderJobService.handleUnSubmitOrder());
    }
}
