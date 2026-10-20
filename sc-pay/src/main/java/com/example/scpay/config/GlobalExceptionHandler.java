package com.example.scpay.config;

import exception.BusinessException;
import com.curry.model.auth.AuthConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import response.ResponseDto;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseDto<Void> handleBusinessException(BusinessException e) {
        return ResponseDto.<Void>error("BUSINESS_ERROR", e.getMessage()).traceId(traceId());
    }

    @ExceptionHandler(Exception.class)
    public ResponseDto<Void> handleException(Exception e) {
        String traceId = traceId();
        log.error("[GlobalExceptionHandler] unexpected error traceId={}", traceId, e);
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
