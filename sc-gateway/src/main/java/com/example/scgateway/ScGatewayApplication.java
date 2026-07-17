package com.example.scgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class ScGatewayApplication {

    /**
     * Spring Boot 启动入口。
     */
    public static void main(String[] args) {
        SpringApplication.run(ScGatewayApplication.class, args);
    }

}
