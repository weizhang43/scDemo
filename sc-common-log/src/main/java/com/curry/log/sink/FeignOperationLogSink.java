package com.curry.log.sink;

import com.curry.log.client.OperationLogClient;
import com.curry.model.OperationLog;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认出口：经 Feign 把操作日志转发到 sc-user 落库。
 * 仅当上下文中没有其它 OperationLogSink 时生效(sc-user 提供直写实现覆盖)。
 * 发送失败只记日志，不影响业务(调用方已在异步线程中执行)。
 */
@Slf4j
public class FeignOperationLogSink implements OperationLogSink {

    private final OperationLogClient client;

    public FeignOperationLogSink(OperationLogClient client) {
        this.client = client;
    }

    @Override
    public void save(OperationLog operationLog) {
        try {
            client.save(operationLog);
        } catch (Exception e) {
            log.error("[OperationLog] Feign 转发落库失败 module={} method={}",
                    operationLog.getModule(), operationLog.getMethod(), e);
        }
    }
}
