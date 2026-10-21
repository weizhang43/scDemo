package com.example.scgateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关全局跨域配置。
 */
@Configuration
public class CorsConfig {

    /** 预检请求（OPTIONS）结果的浏览器缓存时长：1 小时 */
    private static final long PREFLIGHT_MAX_AGE_SECONDS = 3600L;

    /**
     * 允许的来源，支持通配模式。默认 http://*:8888：前端代理固定 8888 端口，
     * 但浏览器对 POST 总是携带 Origin 头，用 localhost 或局域网 IP 访问时 Origin 不同，
     * 若只配 localhost 精确值，IP 访问的所有写操作都会被 CORS 过滤器以 403 拒绝。
     */
    @Value("${security.cors.allowed-origins:http://*:8888}")
    private String allowedOrigins;

    /**
     * 创建响应式跨域过滤器，仅允许配置的可信来源携带凭证。
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = new ArrayList<>();
        for (String origin : allowedOrigins.split(",")) {
            if (!origin.trim().isEmpty()) {
                origins.add(origin.trim());
            }
        }
        config.setAllowedOriginPatterns(origins);
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");
        config.addAllowedHeader("Authorization");
        config.addAllowedHeader("Content-Type");
        config.addAllowedHeader("X-Requested-With");
        config.setAllowCredentials(true);
        config.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
