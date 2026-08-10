package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.StudyPlan;
import response.ResponseDto;

import java.util.Date;

public interface StudyPlanService extends IService<StudyPlan> {

    /**
     * 分页查询学习计划列表，按计划日期升序。
     *
     * @param scope    all-全部；其余值只查计划日期在今天及以后的计划
     * @param planDate 指定计划日期时忽略 scope，只查该天
     */
    ResponseDto<StudyPlan> pageQuery(Integer pageNum, Integer pageSize, String scope, Date planDate);

    /**
     * 查询单条计划。
     */
    ResponseDto<StudyPlan> getDetail(Long planId);

    /**
     * 发布学习计划，同一计划日期只能有一条。
     */
    ResponseDto<StudyPlan> addPlan(StudyPlan plan);

    /**
     * 修改计划的标题、内容与计划日期。
     */
    ResponseDto<StudyPlan> updatePlan(StudyPlan plan);

    /**
     * 删除计划。
     */
    ResponseDto<StudyPlan> removePlan(Long planId);

    /**
     * 将计划标记为已完成并记录完成日期，已超期的计划允许补完成。
     */
    ResponseDto<StudyPlan> completePlan(Long planId);

    /**
     * 定时任务调用：将计划日期已过且仍未完成的计划置为已超期，返回受影响行数。
     */
    int handleExpired();
}
