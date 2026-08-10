package com.example.scorder.config;

import exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import response.ResponseDto;

/**
 * 全局异常处理：业务异常转错误响应（提示语透传），其余异常兜底记日志返回统一提示。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：提示语直接返回给调用方，HTTP 200 + code=500，保持 Feign 调用方按 code 判断的语义 */
    @ExceptionHandler(BusinessException.class)
    public ResponseDto<Void> handleBusinessException(BusinessException e) {
        return ResponseDto.error(e.getMessage());
    }

    /**
     * 非业务异常兜底：记录完整堆栈，对外只回统一提示，不泄露内部细节。
     */
    @ExceptionHandler(Exception.class)
    public ResponseDto<Void> handleException(Exception e) {
        LOGGER.error("[GlobalExceptionHandler] unexpected error", e);
        return ResponseDto.error("系统繁忙，请稍后重试");
    }
}
