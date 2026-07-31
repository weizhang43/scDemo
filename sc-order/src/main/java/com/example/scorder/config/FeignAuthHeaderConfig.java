package com.example.scorder.config;

import com.curry.model.auth.AuthConstant;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Feign 请求拦截器：把网关注入到当前请求的内部鉴权 Header 透传给下游服务。
 * 下游 sc-product 的 InnerAuthFilter 要求请求携带 X-User-Id，否则直接返回 401。
 */
@Configuration
public class FeignAuthHeaderConfig {

    /** 服务间内部调用令牌，供无用户上下文的定时任务/MQ 消费线程回退鉴权 */
    @Value("${" + AuthConstant.INNER_TOKEN_PROPERTY + ":}")
    private String innerToken;

    /**
     * 创建 Feign 请求拦截器，从当前 Servlet 请求取出网关注入的鉴权头并复制到下游 Feign 调用。
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                // 定时任务/MQ 消费线程无用户上下文，回退内部令牌供下游 InnerAuthFilter 放行
                if (innerToken != null && !innerToken.isEmpty()) {
                    template.header(AuthConstant.HEADER_X_INNER_TOKEN, innerToken);
                }
                return;
            }
            HttpServletRequest request = attrs.getRequest();
            header(request, AuthConstant.HEADER_X_USER_ID, template);
            header(request, AuthConstant.HEADER_X_USER_NAME, template);
            header(request, AuthConstant.HEADER_X_REAL_NAME, template);
            header(request, AuthConstant.HEADER_X_USER_TYPE, template);
            header(request, AuthConstant.HEADER_AUTHORIZATION, template);
        };
    }

    /**
     * 当原请求中存在指定头时，将其复制到 Feign 请求模板上。
     */
    private void header(HttpServletRequest request, String name, RequestTemplate template) {
        String value = request.getHeader(name);
        if (value != null && !value.isEmpty()) {
            template.header(name, value);
        }
    }
}
