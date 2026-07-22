package com.example.scuser.aspect;

import com.curry.model.OperationLog;
import com.curry.model.auth.AuthConstant;
import com.example.scuser.annotation.OpLog;
import com.example.scuser.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * 拦截标注 @OpLog 的方法，组装操作日志后异步落库。
 * 落库失败不影响业务；参数/返回值截断，防止大对象撑爆字段。
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    private static final int MAX_SUMMARY = 2000;

    @Autowired
    private OperationLogService operationLogService;

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint pjp, OpLog opLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object ret = null;
        Throwable error = null;
        try {
            ret = pjp.proceed();
            return ret;
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            try {
                record(pjp, opLog, ret, error, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("[OperationLog] 组装操作日志失败", e);
            }
        }
    }

    private void record(ProceedingJoinPoint pjp, OpLog opLog, Object ret,
                        Throwable error, long costMs) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        OperationLog entity = new OperationLog();
        entity.setModule(opLog.module());
        entity.setOpType(opLog.type().name());
        entity.setDescription(opLog.description());
        entity.setMethod(signature.getDeclaringType().getSimpleName()
                + "." + signature.getName());
        entity.setRequestParams(summarize(Arrays.stream(pjp.getArgs())
                .map(this::argSummary)
                .collect(Collectors.joining(", "))));
        entity.setCostMs(costMs);
        entity.setCreateTime(new Date());

        HttpServletRequest request = currentRequest();
        if (request != null) {
            entity.setRequestUri(request.getRequestURI());
            entity.setRequestMethod(request.getMethod());
            entity.setIp(clientIp(request));
            String uId = request.getHeader(AuthConstant.HEADER_X_USER_ID);
            if (uId != null && uId.matches("\\d+")) {
                entity.setUId(Integer.valueOf(uId));
            }
            entity.setUName(request.getHeader(AuthConstant.HEADER_X_USER_NAME));
        }

        if (error != null) {
            entity.setStatus(0);
            entity.setErrorMsg(summarize(error.toString()));
        } else {
            entity.setStatus(1);
            entity.setResponseSummary(ret == null ? null : summarize(ret.toString()));
        }
        operationLogService.saveAsync(entity);
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    private String argSummary(Object arg) {
        if (arg == null) {
            return "null";
        }
        String cn = arg.getClass().getName();
        if (cn.contains("HttpServletRequest") || cn.contains("HttpServletResponse")
                || cn.contains("MultipartFile") || cn.contains("ServletOutputStream")) {
            return arg.getClass().getSimpleName();
        }
        return summarize(arg.toString());
    }

    private static String summarize(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_SUMMARY ? s : s.substring(0, MAX_SUMMARY - 3) + "...";
    }
}
