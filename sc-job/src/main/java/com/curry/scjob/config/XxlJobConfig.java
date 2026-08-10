package com.curry.scjob.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-Job 执行器配置：读取 xxl.job.* 配置项并注册执行器到调度中心。
 */
@Configuration
public class XxlJobConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(XxlJobConfig.class);

    @Value("${xxl.job.admin.addresses:}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken:}")
    private String accessToken;

    @Value("${xxl.job.executor.appname:sc-job}")
    private String appname;

    @Value("${xxl.job.executor.address:}")
    private String address;

    @Value("${xxl.job.executor.ip:}")
    private String ip;

    @Value("${xxl.job.executor.port:9999}")
    private int port;

    @Value("${xxl.job.executor.logpath:./xxl-job/jobhandler}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays:30}")
    private int logRetentionDays;

    /**
     * 创建 XXL-Job 执行器 Bean，注册到调度中心并按配置暴露本机执行端口。
     */
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        LOGGER.info(">>>>>>>>>>> xxl-job init: adminAddresses={}, appname={}", adminAddresses, appname);
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appname);
        executor.setAddress(address);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}
