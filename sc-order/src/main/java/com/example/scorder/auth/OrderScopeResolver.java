package com.example.scorder.auth;

import com.curry.model.auth.AuthConstant;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 从网关注入的请求头解析订单可见范围。
 *
 * 只能在 Controller 层（请求线程内）调用：MQ 消费线程与定时任务线程池里
 * RequestContextHolder 为空，scope 必须在入口解析好再随查询条件传下去。
 */
public final class OrderScopeResolver {

    private OrderScopeResolver() {
    }

    /**
     * 解析当前请求的可见范围。无请求上下文（MQ / 线程池）时不过滤。
     */
    public static OrderScope current() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return OrderScope.unrestricted();
        }
        return from(attrs.getRequest());
    }

    /**
     * 按 X-User-Id / X-User-Type 判定范围。
     * 无 X-User-Id 说明是携带 X-Inner-Token 的服务间调用，不过滤。
     * uType 缺失只可能是绕过新版网关的调用，按最小权限当顾客处理。
     */
    public static OrderScope from(HttpServletRequest request) {
        Integer uId = parseInt(request.getHeader(AuthConstant.HEADER_X_USER_ID));
        if (uId == null) {
            return OrderScope.unrestricted();
        }
        Integer uType = parseInt(request.getHeader(AuthConstant.HEADER_X_USER_TYPE));
        if (uType == null || uType == AuthConstant.U_TYPE_CUSTOMER) {
            return OrderScope.owner(uId);
        }
        return OrderScope.unrestricted();
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
