package com.example.scorder.config;

import exception.BusinessException;
import com.curry.model.auth.AuthConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import response.ResponseDto;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * 全局异常处理：业务异常转错误响应（提示语透传），其余异常兜底记日志返回统一提示。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：提示语直接返回给调用方，HTTP 200 + code=500，保持 Feign 调用方按 code 判断的语义 */
    @ExceptionHandler(BusinessException.class)
    public ResponseDto<Void> handleBusinessException(BusinessException e) {
        return ResponseDto.<Void>error("BUSINESS_ERROR", e.getMessage()).traceId(traceId());
    }

    @ExceptionHandler(Exception.class)
    public ResponseDto<Void> handleException(Exception e) {
        String traceId = traceId();
        LOGGER.error("[GlobalExceptionHandler] unexpected error traceId={}", traceId, e);
        return ResponseDto.<Void>error("INTERNAL_ERROR", "系统繁忙，请稍后重试").traceId(traceId);
    }

    private String traceId() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs == null ? null : attrs.getRequest();
        if (request != null) {
            String traceId = request.getHeader(AuthConstant.HEADER_X_TRACE_ID);
            if (traceId != null && !traceId.isEmpty()) return traceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
