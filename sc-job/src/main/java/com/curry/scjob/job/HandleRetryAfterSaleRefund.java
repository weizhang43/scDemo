package com.curry.scjob.job;

import com.curry.scjob.service.OrderFeignService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import response.ResponseDto;

import static response.ResponseDto.SUCCESS_CODE;

/**
 * 售后退款重试任务
 * 调度名称：handleRetryAfterSaleRefund
 * 逻辑：通过 Feign 触发 sc-order 扫描"同意退款中"但网关退款未成功的售后工单，重调网关退款并推进终态。
 */
@Component
@Slf4j
public class HandleRetryAfterSaleRefund {

    @Autowired
    private OrderFeignService orderFeignService;

    @XxlJob("handleRetryAfterSaleRefund")
    public void execute() {
        long start = System.currentTimeMillis();
        try {
            ResponseDto<Integer> resp = orderFeignService.retryAfterSaleRefund();
            if (resp == null || !SUCCESS_CODE.equals(resp.getCode())) {
                throw new RuntimeException("sc-order retryAfterSaleRefund fail, resp=" + resp);
            }
            log.info("[handleRetryAfterSaleRefund] finish, processed={} costMs={}",
                    resp.getDaoResult(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[handleRetryAfterSaleRefund] error", e);
            throw new RuntimeException(e);
        }
    }
}
