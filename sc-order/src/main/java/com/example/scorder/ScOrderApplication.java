package com.example.scorder;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@MapperScan("com.example.scorder.mapper")
public class ScOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScOrderApplication.class, args);
    }

}
