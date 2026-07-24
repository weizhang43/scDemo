package com.example.scproduct.config;

import com.curry.model.auth.AuthConstant;
import org.springframework.beans.factory.annotation.Value;
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

    /** 服务间内部调用令牌，为空表示未启用内部令牌放行 */
    @Value("${" + AuthConstant.INNER_TOKEN_PROPERTY + ":}")
    private String innerToken;

    /**
     * 内部鉴权过滤：actuator 与商品图片查看（GET /product/image/{fileName}）放行；
     * 携带合法 X-Inner-Token 的服务间调用（sc-job 定时任务等无用户上下文场景）放行；
     * 其它路径要求请求头携带网关注入的 X-User-Id，缺失则直接返回 401。
     * 用 contains 匹配以兼容 context-path 前缀差异。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        boolean isActuator = path.contains("/actuator");
        boolean isImageGet = "GET".equalsIgnoreCase(method)
                && path.contains("/product/image/");
        if (isActuator || isImageGet) {
            chain.doFilter(request, response);
            return;
        }
        String reqInnerToken = request.getHeader(AuthConstant.HEADER_X_INNER_TOKEN);
        if (innerToken != null && !innerToken.isEmpty() && innerToken.equals(reqInnerToken)) {
            chain.doFilter(request, response);
            return;
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
