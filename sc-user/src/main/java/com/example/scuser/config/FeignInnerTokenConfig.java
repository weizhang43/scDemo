package com.example.scuser.config;

import com.curry.model.auth.AuthConstant;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * sc-user 侧 Feign 拦截器：服务间调用统一携带内部令牌。
 * sc-product 的 InnerAuthFilter 要求直连请求带 X-Inner-Token 或网关注入的 X-User-Id，
 * 积分兑换发券等无用户上下文的跨服务调用走内部令牌放行。
 */
@Configuration
public class FeignInnerTokenConfig {

    @Value("${" + AuthConstant.INNER_TOKEN_PROPERTY + ":}")
    private String innerToken;

    @Bean
    public RequestInterceptor innerTokenInterceptor() {
        return template -> {
            if (innerToken != null && !innerToken.isEmpty()) {
                template.header(AuthConstant.HEADER_X_INNER_TOKEN, innerToken);
            }
        };
    }
}
