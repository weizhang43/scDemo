package com.curry.scjob.config;

import com.curry.model.auth.AuthConstant;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 请求拦截器：定时任务无用户上下文，统一携带 X-Inner-Token 内部令牌，
 * 供下游服务（如 sc-product 的 InnerAuthFilter）校验放行。
 */
@Configuration
public class FeignInnerTokenConfig {

    @Value("${" + AuthConstant.INNER_TOKEN_PROPERTY + ":}")
    private String innerToken;

    /**
     * 注册 Feign 请求拦截器：配置了内部令牌时，为每次请求附加 X-Inner-Token 请求头。
     */
    @Bean
    public RequestInterceptor innerTokenRequestInterceptor() {
        return template -> {
            if (innerToken != null && !innerToken.isEmpty()) {
                template.header(AuthConstant.HEADER_X_INNER_TOKEN, innerToken);
            }
        };
    }
}
