package com.example.scpay;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.example.scpay.mapper")
@EnableScheduling
public class ScPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScPayApplication.class, args);
    }
}
