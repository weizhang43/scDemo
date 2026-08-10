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

    /** 日志发送线程池核心线程数 */
    private static final int CORE_POOL_SIZE = 2;
    /** 日志发送线程池最大线程数 */
    private static final int MAX_POOL_SIZE = 4;
    /** 空闲线程存活时间（秒） */
    private static final long KEEP_ALIVE_SECONDS = 60L;
    /** 有界队列容量，防止日志积压压垮内存 */
    private static final int QUEUE_CAPACITY = 1000;

    /**
     * 日志发送专用线程池：有界队列 + CallerRuns，避免积压压垮内存，
     * 极端情况下回退到调用线程执行(仅拖慢单次请求，不丢日志)。
     */
    @Bean
    @ConditionalOnMissingBean(name = "operationLogExecutor")
    public Executor operationLogExecutor() {
        return new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
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

    /**
     * 注册共享操作日志切面，组装日志后交给异步线程池经 Sink 落地。
     */
    @Bean
    @ConditionalOnMissingBean(OperationLogAspect.class)
    public OperationLogAspect operationLogAspect(OperationLogSink sink, Executor operationLogExecutor) {
        return new OperationLogAspect(sink, operationLogExecutor);
    }
}
