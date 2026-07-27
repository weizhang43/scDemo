package com.curry.log.client;

import com.curry.model.auth.AuthConstant;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * 操作日志 Feign 专用配置：固定携带内部令牌 X-Inner-Token，
 * 不依赖各业务模块的全局 Feign 拦截器(日志发送发生在无请求上下文的异步线程)。
 * 注意：不加 @Configuration，避免被组件扫描当成全局拦截器污染其它 Feign 客户端。
 */
public class LogFeignConfig {

    @Value("${" + AuthConstant.INNER_TOKEN_PROPERTY + ":}")
    private String innerToken;

    @Bean
    public RequestInterceptor operationLogInnerTokenInterceptor() {
        return template -> {
            if (innerToken != null && !innerToken.isEmpty()) {
                template.header(AuthConstant.HEADER_X_INNER_TOKEN, innerToken);
            }
        };
    }
}
