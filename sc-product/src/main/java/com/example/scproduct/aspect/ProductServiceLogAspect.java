package com.example.scproduct.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * ProductService 方法切面：统一记录入参摘要、返回 code、异常与耗时。
 * 设计要点：
 *  - 只切实现类，避免 BaseMapper/IService 默认方法噪音；
 *  - 参数和返回值都用 toSummary 截断到 200 字符，防止大对象刷屏；
 *  - 成功调用：耗时 >= 100ms 用 info，< 100ms 用 debug；
 *  - 抛异常：error 级别输出。
 */
@Slf4j
@Aspect
@Component
public class ProductServiceLogAspect {

    private static final int MAX_SUMMARY = 200;
    private static final long SLOW_THRESHOLD_MS = 100L;

    @Around("execution(* com.example.scproduct.service.impl.ProductServiceImpl.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        String args = summarize(Arrays.stream(pjp.getArgs())
                .map(this::argSummary)
                .collect(Collectors.joining(", ")));

        long start = System.currentTimeMillis();
        try {
            Object ret = pjp.proceed();
            long cost = System.currentTimeMillis() - start;
            String retSummary = ret == null ? "null" : summarize(ret.toString());
            if (cost >= SLOW_THRESHOLD_MS) {
                log.info("[ProductService] {} done, args=[{}], ret=[{}], costMs={}",
                        method, args, retSummary, cost);
            } else {
                log.debug("[ProductService] {} done, args=[{}], ret=[{}], costMs={}",
                        method, args, retSummary, cost);
            }
            return ret;
        } catch (Throwable e) {
            long cost = System.currentTimeMillis() - start;
            log.error("[ProductService] {} failed, args=[{}], costMs={}, err={}",
                    method, args, cost, e.toString(), e);
            throw e;
        }
    }

    private String argSummary(Object arg) {
        if (arg == null) {
            return "null";
        }
        // HttpServletResponse/HttpServletRequest 等不宜 toString，直接用类名
        String cn = arg.getClass().getName();
        if (cn.contains("HttpServletResponse") || cn.contains("HttpServletRequest")
                || cn.contains("ServletOutputStream")) {
            return arg.getClass().getSimpleName();
        }
        return summarize(arg.toString());
    }

    private static String summarize(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= MAX_SUMMARY ? s : s.substring(0, MAX_SUMMARY) + "...";
    }
}
