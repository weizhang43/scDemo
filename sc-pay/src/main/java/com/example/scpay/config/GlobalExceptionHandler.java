package com.example.scpay.config;

import exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import response.ResponseDto;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：提示语直接返回给调用方，HTTP 200 + code=500，保持调用方按 code 判断的语义 */
    @ExceptionHandler(BusinessException.class)
    public ResponseDto<Void> handleBusinessException(BusinessException e) {
        return ResponseDto.error(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseDto<Void> handleException(Exception e) {
        log.error("[GlobalExceptionHandler] unexpected error", e);
        return ResponseDto.error("系统繁忙，请稍后重试");
    }
}
