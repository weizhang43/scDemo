package com.example.scuser.sink;

import com.curry.log.sink.OperationLogSink;
import com.curry.model.OperationLog;
import com.example.scuser.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * sc-user 是操作日志的唯一落库方：本模块内的 @OpLog 日志直接写库，
 * 不经 Feign 自己调自己。该 Bean 存在时，共享 starter 的 FeignOperationLogSink 自动让位。
 */
@Component
public class DbOperationLogSink implements OperationLogSink {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbOperationLogSink.class);

    @Autowired
    private OperationLogService operationLogService;

    @Override
    public void save(OperationLog operationLog) {
        try {
            operationLogService.save(operationLog);
        } catch (Exception e) {
            LOGGER.error("[OperationLog] 直写落库失败 log={}", operationLog, e);
        }
    }
}
