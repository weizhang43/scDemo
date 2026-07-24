package com.curry.scjob.job;

import com.curry.scjob.service.OrderFeignService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class HandleUnSubmitOrder {

    @Autowired
    private OrderFeignService orderFeignService;

    @XxlJob("handleUnSubmitOrder")
    public void execute() {
        long start = System.currentTimeMillis();
        try {
            ResponseDto<Integer> resp = orderFeignService.handleUnSubmitOrder();
            if (resp == null || !SUCCESS_CODE.equals(resp.getCode())) {
                throw new RuntimeException("sc-order handleUnSubmitOrder fail, resp=" + resp);
            }
            log.info("[handleUnSubmitOrder] finish, scanned={} costMs={}",
                    resp.getDaoResult(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[handleUnSubmitOrder] error", e);
            throw new RuntimeException(e);
        }
    }
}
