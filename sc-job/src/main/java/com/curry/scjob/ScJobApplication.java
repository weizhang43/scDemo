package com.curry.scjob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ScJobApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScJobApplication.class, args);
    }

}
