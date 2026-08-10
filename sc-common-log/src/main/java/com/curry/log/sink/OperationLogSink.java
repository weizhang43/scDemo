package com.curry.log.sink;

import com.curry.model.OperationLog;

/**
 * 操作日志落地出口。默认实现走 Feign 转发到 sc-user；
 * sc-user 自身注册 @Primary 的直写实现，避免自己 HTTP 调自己。
 */
public interface OperationLogSink {

    /**
     * 持久化一条操作日志（调用方已在异步线程中执行，失败不影响业务）。
     *
     * @param operationLog 待落地的操作日志
     */
    void save(OperationLog operationLog);
}
