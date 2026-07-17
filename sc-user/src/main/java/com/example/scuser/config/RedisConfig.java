package com.example.scuser.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置。
 * 刻意使用 StringRedisTemplate 风格：key/value 都是 String，
 * 业务侧自行 ObjectMapper 序列化/反序列化，避免跨服务 GenericJackson2JsonRedisSerializer
 * 配置差异导致反序列化失败。
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    /**
     * 兼容旧代码：返回一个 value 为 String 序列化的 RedisTemplate<String,Object>，
     * 但实际写入的对象需为 String，否则会调用 toString。
     * 业务侧统一改用 StringRedisTemplate。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(cf);
        StringRedisSerializer s = new StringRedisSerializer();
        template.setKeySerializer(s);
        template.setHashKeySerializer(s);
        template.setValueSerializer(s);
        template.setHashValueSerializer(s);
        template.afterPropertiesSet();
        return template;
    }
}
