package com.example.scuser.sink;

import com.curry.log.sink.OperationLogSink;
import com.curry.model.OperationLog;
import com.example.scuser.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * sc-user 是操作日志的唯一落库方：本模块内的 @OpLog 日志直接写库，
 * 不经 Feign 自己调自己。该 Bean 存在时，共享 starter 的 FeignOperationLogSink 自动让位。
 */
@Slf4j
@Component
public class DbOperationLogSink implements OperationLogSink {

    @Autowired
    private OperationLogService operationLogService;

    @Override
    public void save(OperationLog operationLog) {
        try {
            operationLogService.save(operationLog);
        } catch (Exception e) {
            log.error("[OperationLog] 直写落库失败 log={}", operationLog, e);
        }
    }
}
