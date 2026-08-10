package com.curry.log.aspect;

import com.curry.log.sink.OperationLogSink;
import com.curry.model.OperationLog;
import com.curry.model.annotation.OpLog;
import com.curry.model.auth.AuthConstant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 共享操作日志切面：拦截 @OpLog，在请求线程同步组装 OperationLog(读取 ThreadLocal 中的请求上下文)，
 * 再交给独立线程池异步经 OperationLogSink 落地，避免阻塞业务、也不依赖各模块的 @EnableAsync。
 * 落地失败不影响业务；参数/返回值截断，防止大对象撑爆字段。
 */
@Aspect
public class OperationLogAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogAspect.class);

    /** 参数/返回值/错误信息的最大保留长度 */
    private static final int MAX_SUMMARY = 2000;
    /** 截断后追加的省略号，其长度参与截断位置计算 */
    private static final String ELLIPSIS = "...";

    private final OperationLogSink sink;
    private final Executor executor;

    public OperationLogAspect(OperationLogSink sink, Executor executor) {
        this.sink = sink;
        this.executor = executor;
    }

    /**
     * 环绕通知：业务方法正常执行并透传返回值/异常，finally 中同步组装日志实体后异步落地。
     */
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
                // 组装必须在请求线程同步完成：uId/ip/uri 来自线程本地的请求上下文
                OperationLog entity = build(pjp, opLog, ret, error,
                        System.currentTimeMillis() - start);
                executor.execute(() -> sink.save(entity));
            } catch (Exception e) {
                LOGGER.error("[OperationLog] 组装操作日志失败", e);
            }
        }
    }

    /**
     * 在请求线程同步组装操作日志实体：方法信息、参数摘要、请求上下文、执行结果。
     */
    private OperationLog build(ProceedingJoinPoint pjp, OpLog opLog, Object ret,
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
        return entity;
    }

    /**
     * 获取当前线程绑定的 HttpServletRequest，非 Web 上下文返回 null。
     */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    /**
     * 解析客户端真实 IP：优先 X-Forwarded-For 首段，其次 X-Real-IP，最后取远端地址。
     */
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

    /**
     * 生成单个参数的摘要：Servlet/文件类对象只记类型名，其余记截断后的 toString。
     */
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

    /**
     * 截断超长字符串到 MAX_SUMMARY 长度，防止大对象撑爆日志字段。
     */
    private static String summarize(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_SUMMARY ? s : s.substring(0, MAX_SUMMARY - ELLIPSIS.length()) + ELLIPSIS;
    }
}
