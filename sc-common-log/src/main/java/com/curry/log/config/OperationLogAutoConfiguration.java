package com.curry.log.config;

import com.curry.log.aspect.OperationLogAspect;
import com.curry.log.client.OperationLogClient;
import com.curry.log.sink.FeignOperationLogSink;
import com.curry.log.sink.OperationLogSink;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 操作日志自动装配：任何依赖 sc-common-log 的模块无需改扫描/注解即可生效。
 * - 注册共享切面、异步发送线程池；
 * - 默认出口为 Feign 转发(sc-user 提供 @Primary 直写实现时自动让位)。
 */
@Configuration
@EnableFeignClients(clients = OperationLogClient.class)
public class OperationLogAutoConfiguration {

    /**
     * 日志发送专用线程池：有界队列 + CallerRuns，避免积压压垮内存，
     * 极端情况下回退到调用线程执行(仅拖慢单次请求，不丢日志)。
     */
    @Bean
    @ConditionalOnMissingBean(name = "operationLogExecutor")
    public Executor operationLogExecutor() {
        return new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                r -> {
                    Thread t = new Thread(r, "op-log-sender");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** 默认 Feign 出口；sc-user 内提供 DbOperationLogSink 后此 Bean 不生效 */
    @Bean
    @ConditionalOnMissingBean(OperationLogSink.class)
    public OperationLogSink feignOperationLogSink(OperationLogClient client) {
        return new FeignOperationLogSink(client);
    }

    @Bean
    @ConditionalOnMissingBean(OperationLogAspect.class)
    public OperationLogAspect operationLogAspect(OperationLogSink sink, Executor operationLogExecutor) {
        return new OperationLogAspect(sink, operationLogExecutor);
    }
}
