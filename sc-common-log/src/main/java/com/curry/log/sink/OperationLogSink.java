package com.curry.log.sink;

import com.curry.model.OperationLog;

/**
 * 操作日志落地出口。默认实现走 Feign 转发到 sc-user；
 * sc-user 自身注册 @Primary 的直写实现，避免自己 HTTP 调自己。
 */
public interface OperationLogSink {

    void save(OperationLog operationLog);
}
