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
 * 发货超时自动确认收货任务
 * 调度名称：handleAutoConfirmReceive
 * 逻辑：通过 Feign 触发 sc-order 扫描已发货且发货超过 N 天的订单，异步自动确认收货(3→2)。
 */
@Component
public class HandleAutoConfirmReceive {

    private static final Logger LOGGER = LoggerFactory.getLogger(HandleAutoConfirmReceive.class);

    @Autowired
    private OrderFeignService orderFeignService;

    /**
     * XXL-Job 入口：触发 sc-order 自动确认收货，失败时抛出异常使本次调度标记为失败。
     */
    @XxlJob("handleAutoConfirmReceive")
    public void execute() {
        long start = System.currentTimeMillis();
        try {
            ResponseDto<Integer> resp = orderFeignService.autoConfirmReceive();
            if (resp == null || !SUCCESS_CODE.equals(resp.getCode())) {
                throw new JobExecuteException("sc-order autoConfirmReceive fail, resp=" + resp);
            }
            LOGGER.info("[handleAutoConfirmReceive] finish, scanned={} costMs={}",
                    resp.getDaoResult(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            LOGGER.error("[handleAutoConfirmReceive] error", e);
            throw new JobExecuteException("handleAutoConfirmReceive job error", e);
        }
    }
}
