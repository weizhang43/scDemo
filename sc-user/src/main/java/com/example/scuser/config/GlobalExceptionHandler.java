package com.example.scuser.config;

import exception.BusinessException;
import com.curry.model.auth.AuthConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import response.ResponseDto;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

import java.util.stream.Collectors;

/**
 * 全局异常处理：参数校验、业务异常与兜底异常统一转 ResponseDto。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 参数校验失败：聚合各字段错误提示返回。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseDto<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseDto.<Void>error("INVALID_ARGUMENT", msg).traceId(traceId());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseDto<Void> handleBusinessException(BusinessException e) {
        return ResponseDto.<Void>error("BUSINESS_ERROR", e.getMessage()).traceId(traceId());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseDto<Void> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseDto.<Void>error("INVALID_ARGUMENT", e.getMessage()).traceId(traceId());
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
