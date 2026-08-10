package com.example.scuser.controller;

import com.curry.model.auth.AuthConstant;
import com.example.scuser.service.StudyPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

/**
 * 定时任务触发端点：由 sc-job 的 XXL-Job 处理器通过 Feign 调用。
 * sc-user 无 InnerAuthFilter，故此处自行校验 X-Inner-Token。
 */
@RestController
@RequestMapping("/user/studyPlan/job")
public class StudyPlanJobController {

    @Autowired
    private StudyPlanService studyPlanService;

    /** 服务间内部调用令牌 */
    @Value("${" + AuthConstant.INNER_TOKEN_PROPERTY + ":}")
    private String innerToken;

    /** 将计划日期已过且仍未完成的计划置为已超期 */
    @PostMapping("/handleExpired")
    public ResponseDto<Integer> handleExpired(
            @RequestHeader(value = AuthConstant.HEADER_X_INNER_TOKEN, required = false) String token) {
        if (innerToken == null || innerToken.isEmpty() || !innerToken.equals(token)) {
            return ResponseDto.error("内部令牌校验失败");
        }
        return ResponseDto.success(studyPlanService.handleExpired());
    }
}
