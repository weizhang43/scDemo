package com.example.scproduct.config;

import com.curry.model.auth.AuthConstant;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 防绕过网关过滤器：
 * 仅信任由 Gateway 注入的 X-User-Id 内部 Header。
 * 直连 product 节点（不带 X-User-Id）的请求一律拒绝。
 *
 * 设计说明：业务接口默认要求登录，例外路径在 ignorePaths 中显式列出。
 */
@Component
public class InnerAuthFilter extends OncePerRequestFilter {

    private static final String[] IGNORE_PATHS = {
            "/actuator"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        for (String p : IGNORE_PATHS) {
            if (path.startsWith(p)) {
                chain.doFilter(request, response);
                return;
            }
        }
        String userId = request.getHeader(AuthConstant.HEADER_X_USER_ID);
        if (userId == null || userId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"未通过网关鉴权\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
