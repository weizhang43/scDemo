package com.example.scgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 网关全局跨域配置。
 */
@Configuration
public class CorsConfig {

    /** 预检请求（OPTIONS）结果的浏览器缓存时长：1 小时 */
    private static final long PREFLIGHT_MAX_AGE_SECONDS = 3600L;

    /**
     * 创建响应式跨域过滤器，放行所有来源/方法/请求头，允许携带 Cookie，预检缓存 1 小时。
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
