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
 * 未提交订单超时取消任务
 * 调度名称：handleUnSubmitOrder
 * 逻辑：通过 Feign 触发 sc-order 扫描未提交订单，Redis 超时 key 已失效的异步取消。
 */
@Component
public class HandleUnSubmitOrder {

    private static final Logger LOGGER = LoggerFactory.getLogger(HandleUnSubmitOrder.class);

    @Autowired
    private OrderFeignService orderFeignService;

    /**
     * XXL-Job 入口：触发 sc-order 扫描并取消超时未提交订单，失败时抛出异常使本次调度标记为失败。
     */
    @XxlJob("handleUnSubmitOrder")
    public void execute() {
        long start = System.currentTimeMillis();
        try {
            ResponseDto<Integer> resp = orderFeignService.handleUnSubmitOrder();
            if (resp == null || !SUCCESS_CODE.equals(resp.getCode())) {
                throw new JobExecuteException("sc-order handleUnSubmitOrder fail, resp=" + resp);
            }
            LOGGER.info("[handleUnSubmitOrder] finish, scanned={} costMs={}",
                    resp.getDaoResult(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            LOGGER.error("[handleUnSubmitOrder] error", e);
            throw new JobExecuteException("handleUnSubmitOrder job error", e);
        }
    }
}
