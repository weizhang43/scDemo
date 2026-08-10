package com.curry.scjob.job;

import com.curry.scjob.exception.JobExecuteException;
import com.curry.scjob.service.ProductFeignService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import response.ResponseDto;

import static response.ResponseDto.SUCCESS_CODE;

/**
 * 商品点赞数回写任务
 * 调度名称：flushProductLikeCountJob
 * 逻辑：通过 Feign 触发 sc-product 将 Redis 中缓存的点赞数批量幂等回写到 t_product，
 *       削峰、降低 DB 写压力。
 * 建议在 xxl-job admin 配置较短周期（如每 10~30 秒）调度，且路由策略为「第一个」，多节点下只由一个节点执行。
 */
@Component
public class FlushProductLikeCountJobHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlushProductLikeCountJobHandler.class);

    @Autowired
    private ProductFeignService productFeignService;

    /**
     * XXL-Job 入口：触发 sc-product 批量回写点赞数，失败时抛出异常使本次调度标记为失败。
     */
    @XxlJob("flushProductLikeCountJob")
    public void execute() {
        long start = System.currentTimeMillis();
        try {
            ResponseDto<Integer> resp = productFeignService.flushLikeCount();
            if (resp == null || !SUCCESS_CODE.equals(resp.getCode())) {
                throw new JobExecuteException("sc-product flushLikeCount fail, resp=" + resp);
            }
            LOGGER.info("[flushProductLikeCountJob] finish, flushed={} costMs={}",
                    resp.getDaoResult(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            LOGGER.error("[flushProductLikeCountJob] error", e);
            throw new JobExecuteException("flushProductLikeCountJob job error", e);
        }
    }
}
