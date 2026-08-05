package com.curry.scjob.job;

import com.curry.scjob.service.OrderFeignService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import response.ResponseDto;

import static response.ResponseDto.SUCCESS_CODE;

/**
 * 发货超时自动确认收货任务
 * 调度名称：handleAutoConfirmReceive
 * 逻辑：通过 Feign 触发 sc-order 扫描已发货且发货超过 N 天的订单，异步自动确认收货(3→2)。
 */
@Component
@Slf4j
public class HandleAutoConfirmReceive {

    @Autowired
    private OrderFeignService orderFeignService;

    @XxlJob("handleAutoConfirmReceive")
    public void execute() {
        long start = System.currentTimeMillis();
        try {
            ResponseDto<Integer> resp = orderFeignService.autoConfirmReceive();
            if (resp == null || !SUCCESS_CODE.equals(resp.getCode())) {
                throw new RuntimeException("sc-order autoConfirmReceive fail, resp=" + resp);
            }
            log.info("[handleAutoConfirmReceive] finish, scanned={} costMs={}",
                    resp.getDaoResult(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[handleAutoConfirmReceive] error", e);
            throw new RuntimeException(e);
        }
    }
}
