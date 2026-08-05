package com.example.scproduct.auth;

import com.curry.model.auth.AuthConstant;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 从网关注入的请求头解析可见范围。
 *
 * 只能在 Controller 层（请求线程内）调用：异步导出跑在线程池里，
 * RequestContextHolder 为空，scope 必须在入口解析好再随查询条件传下去。
 */
public final class AudienceResolver {

    private AudienceResolver() {
    }

    /**
     * 解析当前请求的可见范围。无请求上下文（线程池/MQ 消费线程）时不过滤。
     */
    public static AudienceScope current() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return AudienceScope.unrestricted();
        }
        return from(attrs.getRequest());
    }

    /**
     * 按 X-User-Id / X-User-Type 判定范围。
     * 无 X-User-Id 时分两种：携带 X-Inner-Token 的服务间调用（已由 InnerAuthFilter 校验）不过滤；
     * 否则是网关白名单放行的游客请求，按最小权限当顾客处理。
     * uType 缺失只可能是绕过新版网关的调用，按最小权限当顾客处理。
     */
    public static AudienceScope from(HttpServletRequest request) {
        Integer uId = parseInt(request.getHeader(AuthConstant.HEADER_X_USER_ID));
        if (uId == null) {
            String innerToken = request.getHeader(AuthConstant.HEADER_X_INNER_TOKEN);
            return (innerToken == null || innerToken.trim().isEmpty())
                    ? AudienceScope.customer()
                    : AudienceScope.unrestricted();
        }
        Integer uType = parseInt(request.getHeader(AuthConstant.HEADER_X_USER_TYPE));
        if (uType == null || uType == AuthConstant.U_TYPE_CUSTOMER) {
            return AudienceScope.customer();
        }
        if (uType == AuthConstant.U_TYPE_MERCHANT) {
            return AudienceScope.merchant(uId);
        }
        return AudienceScope.unrestricted();
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
