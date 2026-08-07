package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.WorkReport;
import response.ResponseDto;

public interface WorkReportService extends IService<WorkReport> {

    /**
     * 分页查询指定类型（1-日报 2-周报）的报告列表。
     */
    ResponseDto<WorkReport> pageQuery(Integer pageNum, Integer pageSize, Integer type);

    /**
     * 查询单条报告。
     */
    ResponseDto<WorkReport> getDetail(Long reportId);

    /**
     * 新增日报/周报，同类型一天只能新增一次。
     */
    ResponseDto<WorkReport> addReport(WorkReport report);

    /**
     * 日报默认内容模板：从本地 git 仓库统计当日提交生成。
     */
    ResponseDto<String> dailyTemplate();

    /**
     * 周报默认内容模板：从本地 git 仓库统计本周提交、从 GitLab 统计本周合并 MR 数。
     */
    ResponseDto<String> weeklyTemplate(String title);

    /**
     * 修改报告标题与内容。
     */
    ResponseDto<WorkReport> updateReport(WorkReport report);

    /**
     * 删除报告。
     */
    ResponseDto<WorkReport> removeReport(Long reportId);

    /**
     * 将报告内容发送到指定邮箱。
     */
    ResponseDto<WorkReport> sendReport(Long reportId, String email);
}
