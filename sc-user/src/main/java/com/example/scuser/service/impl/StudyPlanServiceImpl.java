package com.example.scuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.StudyPlan;
import com.example.scuser.mapper.StudyPlanMapper;
import com.example.scuser.service.StudyPlanService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import response.ResponseDto;

import java.util.Calendar;
import java.util.Date;

/**
 * 学习计划服务：计划的增删改查、完成标记与超期处理。
 */
@Service
public class StudyPlanServiceImpl extends ServiceImpl<StudyPlanMapper, StudyPlan> implements StudyPlanService {

    private static final String DEFAULT_PUBLISH_NAME = "zhangwei";

    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_FINISHED = 2;
    private static final int STATUS_EXPIRED = 3;

    /** 分页查询默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** scope 取该值时查全部计划，否则只查今天及以后 */
    private static final String SCOPE_ALL = "all";

    @Override
    public ResponseDto<StudyPlan> pageQuery(Integer pageNum, Integer pageSize, String scope, Date planDate) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        LambdaQueryWrapper<StudyPlan> wrapper = new LambdaQueryWrapper<StudyPlan>()
                .orderByAsc(StudyPlan::getPlanDate);
        if (planDate != null) {
            wrapper.eq(StudyPlan::getPlanDate, truncateToDay(planDate));
        } else if (!SCOPE_ALL.equals(scope)) {
            wrapper.ge(StudyPlan::getPlanDate, truncateToDay(new Date()));
        }
        Page<StudyPlan> page = baseMapper.selectPage(new Page<>(current, size), wrapper);
        return ResponseDto.success(page);
    }

    @Override
    public ResponseDto<StudyPlan> getDetail(Long planId) {
        StudyPlan plan = baseMapper.selectById(planId);
        return plan == null ? ResponseDto.error("计划不存在") : ResponseDto.success(plan);
    }

    @Override
    public ResponseDto<StudyPlan> addPlan(StudyPlan plan) {
        if (!StringUtils.hasText(plan.getTitle())) {
            return ResponseDto.error("标题不能为空");
        }
        if (plan.getPlanDate() == null) {
            return ResponseDto.error("计划日期不能为空");
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getPlanDate, plan.getPlanDate()));
        if (count != null && count > 0) {
            return ResponseDto.error("该计划日期已发布过计划");
        }

        Date now = new Date();
        plan.setPlanId(null);
        plan.setPublishName(DEFAULT_PUBLISH_NAME);
        plan.setPublishDate(truncateToDay(now));
        plan.setStatus(STATUS_PUBLISHED);
        plan.setFinishDate(null);
        plan.setCreateTime(now);
        plan.setUpdateTime(now);
        baseMapper.insert(plan);
        return ResponseDto.success(plan);
    }

    @Override
    public ResponseDto<StudyPlan> updatePlan(StudyPlan plan) {
        if (plan.getPlanId() == null) {
            return ResponseDto.error("计划ID不能为空");
        }
        if (!StringUtils.hasText(plan.getTitle())) {
            return ResponseDto.error("标题不能为空");
        }
        if (plan.getPlanDate() == null) {
            return ResponseDto.error("计划日期不能为空");
        }
        if (baseMapper.selectById(plan.getPlanId()) == null) {
            return ResponseDto.error("计划不存在");
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getPlanDate, plan.getPlanDate())
                .ne(StudyPlan::getPlanId, plan.getPlanId()));
        if (count != null && count > 0) {
            return ResponseDto.error("该计划日期已发布过计划");
        }

        StudyPlan update = new StudyPlan();
        update.setPlanId(plan.getPlanId());
        update.setTitle(plan.getTitle());
        update.setContent(plan.getContent());
        update.setPlanDate(plan.getPlanDate());
        update.setUpdateTime(new Date());
        baseMapper.updateById(update);
        return ResponseDto.success(baseMapper.selectById(plan.getPlanId()));
    }

    @Override
    public ResponseDto<StudyPlan> removePlan(Long planId) {
        if (planId == null) {
            return ResponseDto.error("计划ID不能为空");
        }
        int rows = baseMapper.deleteById(planId);
        return rows > 0 ? ResponseDto.success(null) : ResponseDto.error("计划不存在或已删除");
    }

    @Override
    public ResponseDto<StudyPlan> completePlan(Long planId) {
        if (planId == null) {
            return ResponseDto.error("计划ID不能为空");
        }
        StudyPlan exist = baseMapper.selectById(planId);
        if (exist == null) {
            return ResponseDto.error("计划不存在");
        }
        if (exist.getStatus() != null && exist.getStatus() == STATUS_FINISHED) {
            return ResponseDto.error("计划已完成");
        }
        Date now = new Date();
        StudyPlan update = new StudyPlan();
        update.setPlanId(planId);
        update.setStatus(STATUS_FINISHED);
        update.setFinishDate(truncateToDay(now));
        update.setUpdateTime(now);
        baseMapper.updateById(update);
        return ResponseDto.success(baseMapper.selectById(planId));
    }

    @Override
    public int handleExpired() {
        Date now = new Date();
        StudyPlan update = new StudyPlan();
        update.setStatus(STATUS_EXPIRED);
        update.setUpdateTime(now);
        LambdaQueryWrapper<StudyPlan> wrapper = new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getStatus, STATUS_PUBLISHED)
                .lt(StudyPlan::getPlanDate, truncateToDay(now));
        return baseMapper.update(update, wrapper);
    }

    /** 抹掉时分秒，得到当天零点，用于写入 DATE 列与按天比较 */
    private Date truncateToDay(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
}
