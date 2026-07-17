package com.example.scproduct.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * 创建 RestTemplate 实例，用于跨服务 HTTP 调用。
     */
    @Bean
    public RestTemplate setRestTemplate(){
        return new RestTemplate();
    }
}
