package com.curry.scjob.job;

import com.curry.scjob.exception.JobExecuteException;
import com.curry.scjob.service.ProductFeignService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import response.ResponseDto;

import java.text.SimpleDateFormat;
import java.util.Date;

import static response.ResponseDto.SUCCESS_CODE;

/**
 * 商品过期处理任务
 * 调度名称：handleProductExpiredJob
 * 逻辑：通过 Feign 触发 sc-product 扫描所有 is_expired=0 的商品，
 *       若 production_date + shelf_life 天 < 当前日期，则将 is_expired 改为 1；
 *       随后把所有已过期商品统一下架（status 改为 0）。
 */
@Component
public class HandleProductExpiredJobHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HandleProductExpiredJobHandler.class);

    @Autowired
    private ProductFeignService productFeignService;

    /**
     * XXL-Job 入口：标记过期商品，并将已过期商品自动下架。
     */
    @XxlJob("handleProductExpiredJob")
    public void execute() {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        LOGGER.info("[handleProductExpiredJob] start, scanTime={}", now);
        long start = System.currentTimeMillis();
        try {
            ResponseDto<String> resp = productFeignService.markExpired();
            if (resp == null || !SUCCESS_CODE.equals(resp.getCode())) {
                throw new JobExecuteException("sc-product markExpired fail, resp=" + resp);
            }
            LOGGER.info("[handleProductExpiredJob] finish, {} costMs={}",
                    resp.getDaoResult(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            LOGGER.error("[handleProductExpiredJob] error", e);
            throw new JobExecuteException("handleProductExpiredJob job error", e);
        }
    }
}
