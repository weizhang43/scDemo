package com.example.scuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.WorkReport;
import com.example.scuser.config.WorkReportGitProperties;
import com.example.scuser.config.WorkReportGitlabProperties;
import com.example.scuser.mapper.WorkReportMapper;
import com.example.scuser.service.WorkReportService;
import com.example.scuser.util.MailUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import response.ResponseDto;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WorkReportServiceImpl extends ServiceImpl<WorkReportMapper, WorkReport> implements WorkReportService {

    private static final String DEFAULT_CREATE_NAME = "zhangwei";
    private static final int TYPE_DAILY = 1;
    private static final int TYPE_WEEKLY = 2;

    @Autowired
    private MailUtil mailUtil;

    @Autowired
    private WorkReportGitProperties gitProperties;

    @Autowired
    private WorkReportGitlabProperties gitlabProperties;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public ResponseDto<WorkReport> pageQuery(Integer pageNum, Integer pageSize, Integer type) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        LambdaQueryWrapper<WorkReport> wrapper = new LambdaQueryWrapper<WorkReport>()
                .eq(type != null, WorkReport::getType, type)
                .orderByDesc(WorkReport::getCreateTime);
        Page<WorkReport> page = baseMapper.selectPage(new Page<>(current, size), wrapper);
        return ResponseDto.success(page);
    }

    @Override
    public ResponseDto<WorkReport> getDetail(Long reportId) {
        WorkReport report = baseMapper.selectById(reportId);
        return report == null ? ResponseDto.error("报告不存在") : ResponseDto.success(report);
    }

    @Override
    public ResponseDto<WorkReport> addReport(WorkReport report) {
        Integer type = report.getType();
        if (type == null || (type != TYPE_DAILY && type != TYPE_WEEKLY)) {
            return ResponseDto.error("类型不合法");
        }
        if (!StringUtils.hasText(report.getTitle())) {
            return ResponseDto.error("标题不能为空");
        }
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();

        Long count = baseMapper.selectCount(new LambdaQueryWrapper<WorkReport>()
                .eq(WorkReport::getType, type)
                .ge(WorkReport::getCreateTime, todayStart));
        if (count != null && count > 0) {
            return ResponseDto.error(type == TYPE_DAILY ? "今天已添加过日报" : "今天已添加过周报");
        }

        report.setReportId(null);
        report.setCreateName(DEFAULT_CREATE_NAME);
        report.setCreateTime(now);
        report.setUpdateTime(now);
        baseMapper.insert(report);
        return ResponseDto.success(report);
    }

    @Override
    public ResponseDto<WorkReport> updateReport(WorkReport report) {
        if (report.getReportId() == null) {
            return ResponseDto.error("报告ID不能为空");
        }
        if (baseMapper.selectById(report.getReportId()) == null) {
            return ResponseDto.error("报告不存在");
        }
        WorkReport update = new WorkReport();
        update.setReportId(report.getReportId());
        update.setTitle(report.getTitle());
        update.setContent(report.getContent());
        update.setUpdateTime(new Date());
        baseMapper.updateById(update);
        return ResponseDto.success(baseMapper.selectById(report.getReportId()));
    }

    @Override
    public ResponseDto<WorkReport> removeReport(Long reportId) {
        if (reportId == null) {
            return ResponseDto.error("报告ID不能为空");
        }
        int rows = baseMapper.deleteById(reportId);
        return rows > 0 ? ResponseDto.success(null) : ResponseDto.error("报告不存在或已删除");
    }

    @Override
    public ResponseDto<WorkReport> sendReport(Long reportId, String email) {
        if (reportId == null) {
            return ResponseDto.error("报告ID不能为空");
        }
        if (!StringUtils.hasText(email) || !email.matches("^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$")) {
            return ResponseDto.error("邮箱格式不正确");
        }
        WorkReport report = baseMapper.selectById(reportId);
        if (report == null) {
            return ResponseDto.error("报告不存在");
        }
        try {
            mailUtil.sendHtmlTo(email, report.getTitle(), report.getContent() == null ? "" : report.getContent());
        } catch (Exception e) {
            return ResponseDto.error("邮件发送失败：" + e.getMessage());
        }
        return ResponseDto.success(report);
    }

    @Override
    public ResponseDto<String> dailyTemplate() {
        List<String> items = new ArrayList<>();
        int commitCount = 0;
        for (WorkReportGitProperties.Repo repo : gitProperties.getRepos()) {
            List<String> subjects = gitTodaySubjects(repo.getPath());
            commitCount += subjects.size();
            for (String subject : subjects) {
                items.add(repo.getName() + "完成" + subject + "【100%】");
            }
        }
        StringBuilder html = new StringBuilder();
        html.append("<p>各位领导好：</p><p><br></p>");
        html.append("<p>项目：OMS-B2B-NA</p>");
        html.append("<p>【今日完成】</p>");
        if (items.isEmpty()) {
            html.append("<p>暂无</p>");
        } else {
            for (int i = 0; i < items.size(); i++) {
                html.append("<p>").append(i + 1).append("、").append(escapeHtml(items.get(i))).append("</p>");
            }
        }
        html.append("<p>【问题及解决】</p><p>暂无</p>");
        html.append("<p>【代码提交】</p><p>").append(commitCount).append("次</p>");
        html.append("<p>【明天日计划】</p><p>暂无</p>");
        html.append("<p>【风险/阻塞】</p><p>暂无</p>");
        return ResponseDto.success(html.toString());
    }

    @Override
    public ResponseDto<String> weeklyTemplate(String title) {
        Date weekStart = mondayStart();
        String since = new SimpleDateFormat("yyyy-MM-dd 00:00:00").format(weekStart);
        List<String> items = new ArrayList<>();
        int commitCount = 0;
        for (WorkReportGitProperties.Repo repo : gitProperties.getRepos()) {
            List<String> subjects = gitSubjects(repo.getPath(), since);
            commitCount += subjects.size();
            for (String subject : subjects) {
                items.add(repo.getName() + " " + subject + "【100%】");
            }
        }
        int mergedCount = countMergedMrThisWeek(weekStart);

        StringBuilder html = new StringBuilder();
        html.append("<p>周报：").append(escapeHtml(title == null ? "" : title)).append("</p><p><br></p>");
        html.append("<p>各位领导好：</p><p><br></p>");
        html.append("<p>项目：OMS-B2B-NA</p>");
        html.append("<p>【本周目标完成情况】</p>");
        appendBullets(html, items);
        html.append("<p>【本周工作详情】</p>");
        html.append("<p>1. 完成工作：</p>");
        appendBullets(html, items);
        html.append("<p>2. 未完成工作：</p><p>○无</p>");
        html.append("<p>【代码贡献】</p>");
        html.append("<p>●B2B</p>");
        html.append("<p>○PRE合并：").append(mergedCount < 0 ? "获取失败" : mergedCount + "个").append("</p>");
        html.append("<p>○代码提交：").append(commitCount).append("次</p>");
        html.append("<p>【问题与解决】</p><p>● 无</p>");
        html.append("<p>【技术要点】</p><p>● 主要涉及前后端功能联调、问题排查、修复缺陷、保障上线</p>");
        html.append("<p>【下周计划】</p><p>●暂无</p>");
        html.append("<p>【风险与建议】</p><p>●风险：无</p><p>●建议：无</p><p>●协助需求：无</p>");
        return ResponseDto.success(html.toString());
    }

    private void appendBullets(StringBuilder html, List<String> items) {
        if (items.isEmpty()) {
            html.append("<p>●暂无</p>");
        } else {
            for (String item : items) {
                html.append("<p>●").append(escapeHtml(item)).append("</p>");
            }
        }
    }

    /**
     * 本周周一 00:00。
     */
    private Date mondayStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int offset = dow == Calendar.SUNDAY ? 6 : dow - Calendar.MONDAY;
        cal.add(Calendar.DAY_OF_MONTH, -offset);
        return cal.getTime();
    }

    /**
     * 用 OAuth password 登录 GitLab，统计本周（周一起）已合并的 MR 数；失败返回 -1。
     */
    private int countMergedMrThisWeek(Date weekStart) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("grant_type", "password");
            body.put("username", gitlabProperties.getUsername());
            body.put("password", gitlabProperties.getPassword());
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            JsonNode tokenResp = restTemplate.postForObject(gitlabProperties.getBaseUrl() + "/oauth/token",
                    new HttpEntity<>(body, jsonHeaders), JsonNode.class);
            if (tokenResp == null || !tokenResp.hasNonNull("access_token")) {
                log.warn("[周报模板] GitLab 获取 token 失败");
                return -1;
            }
            HttpHeaders authHeaders = new HttpHeaders();
            authHeaders.setBearerAuth(tokenResp.get("access_token").asText());
            String after = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(weekStart);
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    gitlabProperties.getBaseUrl()
                            + "/api/v4/merge_requests?scope=created_by_me&state=merged&per_page=100&updated_after={after}",
                    HttpMethod.GET, new HttpEntity<>(authHeaders), JsonNode.class, after);
            JsonNode list = resp.getBody();
            if (list == null || !list.isArray()) {
                return -1;
            }
            int count = 0;
            for (JsonNode mr : list) {
                if (mr.hasNonNull("merged_at")
                        && !OffsetDateTime.parse(mr.get("merged_at").asText()).toInstant().isBefore(weekStart.toInstant())) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            log.warn("[周报模板] GitLab 统计合并 MR 失败", e);
            return -1;
        }
    }

    /**
     * 统计本地仓库当日（零点起）指定作者的提交标题，仓库不可用时返回空列表不阻断模板生成。
     */
    private List<String> gitTodaySubjects(String repoPath) {
        return gitSubjects(repoPath, "midnight");
    }

    /**
     * 统计本地仓库自 since 起指定作者的提交标题，仓库不可用时返回空列表不阻断模板生成。
     */
    private List<String> gitSubjects(String repoPath, String since) {
        List<String> subjects = new ArrayList<>();
        File dir = repoPath == null ? null : new File(repoPath);
        if (dir == null || !dir.isDirectory()) {
            log.warn("[日报模板] 仓库目录不存在: {}", repoPath);
            return subjects;
        }
        try {
            Process process = new ProcessBuilder("git", "log", "--since=" + since, "--no-merges",
                    "--author=" + gitProperties.getAuthor(), "--pretty=%s")
                    .directory(dir)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (StringUtils.hasText(line)) {
                        subjects.add(line.trim());
                    }
                }
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("[日报模板] git log 超时: {}", repoPath);
                return new ArrayList<>();
            }
            if (process.exitValue() != 0) {
                log.warn("[日报模板] git log 失败, exit={}, repo={}, output={}", process.exitValue(), repoPath, subjects);
                return new ArrayList<>();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("[日报模板] git log 异常, repo={}", repoPath, e);
            return new ArrayList<>();
        }
        return subjects;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
