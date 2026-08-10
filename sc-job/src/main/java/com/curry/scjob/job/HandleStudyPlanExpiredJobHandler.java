package com.curry.scjob.job;

import com.curry.scjob.exception.JobExecuteException;
import com.curry.scjob.service.UserFeignService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import response.ResponseDto;

import static response.ResponseDto.SUCCESS_CODE;

/**
 * 学习计划超期处理任务
 * 调度名称：handleStudyPlanExpired
 * 逻辑：通过 Feign 触发 sc-user 将计划日期已过且仍未完成的学习计划置为已超期。
 */
@Component
public class HandleStudyPlanExpiredJobHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HandleStudyPlanExpiredJobHandler.class);

    @Autowired
    private UserFeignService userFeignService;

    /**
     * XXL-Job 入口：每天零点触发 sc-user 处理超期学习计划，失败时抛出异常使本次调度标记为失败。
     */
    @XxlJob("handleStudyPlanExpired")
    public void execute() {
        long start = System.currentTimeMillis();
        try {
            ResponseDto<Integer> resp = userFeignService.handleStudyPlanExpired();
            if (resp == null || !SUCCESS_CODE.equals(resp.getCode())) {
                throw new JobExecuteException("sc-user handleStudyPlanExpired fail, resp=" + resp);
            }
            LOGGER.info("[handleStudyPlanExpired] finish, expired={} costMs={}",
                    resp.getDaoResult(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            LOGGER.error("[handleStudyPlanExpired] error", e);
            throw new JobExecuteException("handleStudyPlanExpired job error", e);
        }
    }
}
