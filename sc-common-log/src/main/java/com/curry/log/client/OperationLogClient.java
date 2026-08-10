package com.curry.log.client;

import com.curry.model.OperationLog;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 把操作日志转发给 sc-user 落库的 Feign 客户端。
 * 路径含 sc-user 的 context-path(/sc-user)；鉴权头由 LogFeignConfig 统一附加 X-Inner-Token。
 */
@FeignClient(name = "sc-user", contextId = "operationLogClient",
        configuration = LogFeignConfig.class)
public interface OperationLogClient {

    /**
     * 调用 sc-user 内部接口保存一条操作日志。
     *
     * @param operationLog 待落地的操作日志
     */
    @PostMapping("/sc-user/user/operationLog/inner/save")
    void save(@RequestBody OperationLog operationLog);
}
