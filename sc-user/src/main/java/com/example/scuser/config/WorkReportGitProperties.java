package com.example.scuser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作日报模板的 git 统计配置：提交作者 + 本地仓库列表。
 */
@Data
@Component
@ConfigurationProperties(prefix = "work-report.git")
public class WorkReportGitProperties {

    private String author;

    private List<Repo> repos = new ArrayList<>();

    @Data
    public static class Repo {
        /** 展示名，如 oms na */
        private String name;
        /** 本地仓库目录 */
        private String path;
    }
}
