package com.example.scuser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 工作周报模板的 GitLab 统计配置：用 OAuth password 方式登录后查询本周已合并 MR 数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "work-report.gitlab")
public class WorkReportGitlabProperties {

    private String baseUrl;

    private String username;

    private String password;
}
