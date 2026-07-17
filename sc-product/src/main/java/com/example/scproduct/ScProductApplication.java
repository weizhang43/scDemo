package com.example.scproduct;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.scproduct.mapper")
public class ScProductApplication {

    /**
     * Spring Boot 启动入口，启用 Mapper 扫描。
     */
    public static void main(String[] args) {
        SpringApplication.run(ScProductApplication.class, args);
    }

}
