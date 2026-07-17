package com.example.scgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
public class RedisConfig {

    /**
     * 创建响应式 String Redis 模板，用于网关侧直接读写原始字符串，规避对象类型跨服务反序列化问题。
     */
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory cf) {
        return new ReactiveStringRedisTemplate(cf);
    }
}
