package com.curry.scjob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * sc-job 启动类：xxl-job 执行器薄调度服务，任务逻辑经 Feign 下沉到各业务模块。
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableFeignClients
public class ScJobApplication {

    /**
     * Spring Boot 启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ScJobApplication.class, args);
    }

}
