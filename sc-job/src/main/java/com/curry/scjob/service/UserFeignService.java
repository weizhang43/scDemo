package com.curry.scjob.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import response.ResponseDto;

@Component
@FeignClient(value = "sc-user", contextId = "sc-user", path = "/sc-user")
public interface UserFeignService {

    @PostMapping("/user/studyPlan/job/handleExpired")
    ResponseDto<Integer> handleStudyPlanExpired();
}
