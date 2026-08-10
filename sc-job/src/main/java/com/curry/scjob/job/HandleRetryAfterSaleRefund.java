package com.curry.scjob.job;

import com.curry.scjob.exception.JobExecuteException;
import com.curry.scjob.service.OrderFeignService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class HandleRetryAfterSaleRefund {

    private static final Logger LOGGER = LoggerFactory.getLogger(HandleRetryAfterSaleRefund.class);

    @Autowired
    private OrderFeignService orderFeignService;

    /**
     * XXL-Job 入口：触发 sc-order 重试售后退款，失败时抛出异常使本次调度标记为失败。
     */
    @XxlJob("handleRetryAfterSaleRefund")
    public void execute() {
        long start = System.currentTimeMillis();
        try {
            ResponseDto<Integer> resp = orderFeignService.retryAfterSaleRefund();
            if (resp == null || !SUCCESS_CODE.equals(resp.getCode())) {
                throw new JobExecuteException("sc-order retryAfterSaleRefund fail, resp=" + resp);
            }
            LOGGER.info("[handleRetryAfterSaleRefund] finish, processed={} costMs={}",
                    resp.getDaoResult(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            LOGGER.error("[handleRetryAfterSaleRefund] error", e);
            throw new JobExecuteException("handleRetryAfterSaleRefund job error", e);
        }
    }
}
