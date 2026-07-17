package com.example.scorder.config;

import com.curry.model.auth.AuthConstant;
import feign.RequestInterceptor;
import feign.RequestTemplate;
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

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return;
            }
            HttpServletRequest request = attrs.getRequest();
            header(request, AuthConstant.HEADER_X_USER_ID, template);
            header(request, AuthConstant.HEADER_X_USER_NAME, template);
            header(request, AuthConstant.HEADER_X_REAL_NAME, template);
            header(request, AuthConstant.HEADER_AUTHORIZATION, template);
        };
    }

    private void header(HttpServletRequest request, String name, RequestTemplate template) {
        String value = request.getHeader(name);
        if (value != null && !value.isEmpty()) {
            template.header(name, value);
        }
    }
}
