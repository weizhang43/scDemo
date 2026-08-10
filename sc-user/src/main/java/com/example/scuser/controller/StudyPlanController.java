package com.example.scuser.controller;

import com.curry.model.StudyPlan;
import com.example.scuser.service.StudyPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

import java.util.Date;

@RestController
@RequestMapping("/user/studyPlan")
public class StudyPlanController {

    @Autowired
    private StudyPlanService studyPlanService;

    @GetMapping("/page")
    public ResponseDto<StudyPlan> page(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                       @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                       @RequestParam(value = "scope", required = false) String scope,
                                       @RequestParam(value = "planDate", required = false)
                                       @DateTimeFormat(pattern = "yyyy-MM-dd") Date planDate) {
        return studyPlanService.pageQuery(pageNum, pageSize, scope, planDate);
    }

    @GetMapping("/{planId}")
    public ResponseDto<StudyPlan> get(@PathVariable("planId") Long planId) {
        return studyPlanService.getDetail(planId);
    }

    /** 发布学习计划，同一计划日期只能发布一条 */
    @PostMapping
    public ResponseDto<StudyPlan> add(@RequestBody StudyPlan plan) {
        return studyPlanService.addPlan(plan);
    }

    @PutMapping
    public ResponseDto<StudyPlan> update(@RequestBody StudyPlan plan) {
        return studyPlanService.updatePlan(plan);
    }

    @DeleteMapping("/{planId}")
    public ResponseDto<StudyPlan> delete(@PathVariable("planId") Long planId) {
        return studyPlanService.removePlan(planId);
    }

    /** 标记计划为已完成并记录完成日期 */
    @PostMapping("/complete")
    public ResponseDto<StudyPlan> complete(@RequestParam("planId") Long planId) {
        return studyPlanService.completePlan(planId);
    }
}
