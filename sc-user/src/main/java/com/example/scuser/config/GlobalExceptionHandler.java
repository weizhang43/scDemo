package com.example.scuser.config;

import exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import response.ResponseDto;

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
        return ResponseDto.error(msg);
    }

    /** 业务异常：提示语直接返回给调用方，HTTP 200 + code=500，保持 Feign 调用方按 code 判断的语义 */
    @ExceptionHandler(BusinessException.class)
    public ResponseDto<Void> handleBusinessException(BusinessException e) {
        return ResponseDto.error(e.getMessage());
    }

    /**
     * 兜底异常：记录堆栈并返回通用提示。
     */
    @ExceptionHandler(Exception.class)
    public ResponseDto<Void> handleException(Exception e) {
        LOGGER.error("[GlobalExceptionHandler] unexpected error", e);
        return ResponseDto.error("系统繁忙，请稍后重试");
    }
}
