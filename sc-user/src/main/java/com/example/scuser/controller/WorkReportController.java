package com.example.scuser.controller;

import com.curry.model.WorkReport;
import com.example.scuser.service.WorkReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import response.ResponseDto;

@RestController
@RequestMapping("/user/workReport")
public class WorkReportController {

    @Autowired
    private WorkReportService workReportService;

    @GetMapping("/page")
    public ResponseDto<WorkReport> page(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                        @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                        @RequestParam(value = "type", required = false) Integer type) {
        return workReportService.pageQuery(pageNum, pageSize, type);
    }

    /** 日报默认内容模板（含当日 git 提交统计） */
    @GetMapping("/dailyTemplate")
    public ResponseDto<String> dailyTemplate() {
        return workReportService.dailyTemplate();
    }

    /** 周报默认内容模板（含本周 git 提交与 GitLab 合并 MR 统计） */
    @GetMapping("/weeklyTemplate")
    public ResponseDto<String> weeklyTemplate(@RequestParam(value = "title", required = false) String title) {
        return workReportService.weeklyTemplate(title);
    }

    @GetMapping("/{reportId}")
    public ResponseDto<WorkReport> get(@PathVariable("reportId") Long reportId) {
        return workReportService.getDetail(reportId);
    }

    /** 新增日报/周报，同类型一天只能新增一次 */
    @PostMapping
    public ResponseDto<WorkReport> add(@RequestBody WorkReport report) {
        return workReportService.addReport(report);
    }

    @PutMapping
    public ResponseDto<WorkReport> update(@RequestBody WorkReport report) {
        return workReportService.updateReport(report);
    }

    @DeleteMapping("/{reportId}")
    public ResponseDto<WorkReport> delete(@PathVariable("reportId") Long reportId) {
        return workReportService.removeReport(reportId);
    }

    /** 将报告内容发送到指定邮箱 */
    @PostMapping("/send")
    public ResponseDto<WorkReport> send(@RequestParam("reportId") Long reportId,
                                        @RequestParam("email") String email) {
        return workReportService.sendReport(reportId, email);
    }
}
