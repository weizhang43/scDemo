package com.curry.log.config;

import com.curry.log.aspect.OperationLogAspect;
import com.curry.log.client.OperationLogClient;
import com.curry.log.mq.MqOperationLogSink;
import com.curry.log.mq.OperationLogMqConst;
import com.curry.log.sink.FeignOperationLogSink;
import com.curry.log.sink.OperationLogSink;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 操作日志自动装配：任何依赖 sc-common-log 的模块无需改扫描/注解即可生效。
 *
 * 改造说明（接入 RabbitMQ）：
 *  - 新增默认出口 MqOperationLogSink：把日志投递到 RabbitMQ 队列，由 sc-user 监听落库；
 *  - 旧 FeignOperationLogSink 保留作为 fallback，通过 op-log.sink=feign 显式切回；
 *  - 共享 starter 自身声明 exchange/queue/binding：避免依赖 sc-user 一侧声明，
 *    任何引入本 starter 的模块启动后队列即被自动创建（幂等，多次声明不冲突）；
 *  - 注册 Jackson2JsonMessageConverter：保证 OperationLog 序列化为 JSON，
 *    sc-user 监听端按同类型反序列化（Date 字段 Jackson 默认输出 timestamp，
 *    sc-user 监听端用同 Jackson 配置反序列化无障碍）。
 *
 * sink 选择规则：
 *  - op-log.sink 缺省或 = mq → MqOperationLogSink 生效（默认）
 *  - op-log.sink = feign → FeignOperationLogSink 生效（兜底）
 *  - 业务方自定义 Bean → 优先级最高（@ConditionalOnMissingBean 让位）
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
     * 极端情况下回退到调用线程执行（仅拖慢单次请求，不丢日志）。
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

    /**
     * RabbitMQ JSON 消息转换器：替代默认 SimpleMessageConverter。
     *  - OperationLog 含 Date/Long 等类型，SimpleMessageConverter 在反序列化时类型推断弱；
     *  - Jackson 序列化为 JSON，sc-user 监听端按 OperationLog 类型反序列化无障碍。
     * 容器中仅此一个 MessageConverter Bean 时，Spring Boot 自动应用到 RabbitTemplate。
     *
     * 仅在 op-log.sink=mq（默认）时创建：避免未部署 RabbitMQ 的环境因 MessageConverter 触发 RabbitAdmin declare。
     */
    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    @ConditionalOnProperty(prefix = "op-log", name = "sink", havingValue = "mq", matchIfMissing = true)
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 操作日志交换机（direct 类型）。
     * 共享 starter 声明：保证任何引入本 starter 的模块启动后交换机即创建。
     * 仅在 op-log.sink=mq 时声明：未启用 MQ 的环境不创建 AMQP Bean，避免 RabbitAdmin 触发连接。
     */
    @Bean
    @ConditionalOnProperty(prefix = "op-log", name = "sink", havingValue = "mq", matchIfMissing = true)
    public DirectExchange operationLogExchange() {
        return new DirectExchange(OperationLogMqConst.EXCHANGE_OPERATION_LOG);
    }

    /**
     * 操作日志队列：durable 持久化，broker 重启不丢消息。
     * 仅在 op-log.sink=mq 时声明。
     */
    @Bean
    @ConditionalOnProperty(prefix = "op-log", name = "sink", havingValue = "mq", matchIfMissing = true)
    public Queue operationLogQueue() {
        return QueueBuilder.durable(OperationLogMqConst.QUEUE_OPERATION_LOG).build();
    }

    /**
     * 绑定队列到交换机：按 routing.key.operation.log 路由。
     * 仅在 op-log.sink=mq 时声明。
     */
    @Bean
    @ConditionalOnProperty(prefix = "op-log", name = "sink", havingValue = "mq", matchIfMissing = true)
    public Binding operationLogBinding() {
        return BindingBuilder.bind(operationLogQueue())
                .to(operationLogExchange())
                .with(OperationLogMqConst.ROUTING_KEY_OPERATION_LOG);
    }

    /**
     * 默认出口：MQ 投递。
     * op-log.sink 缺省或 = mq 时生效。
     */
    @Bean
    @ConditionalOnMissingBean(OperationLogSink.class)
    @ConditionalOnProperty(prefix = "op-log", name = "sink", havingValue = "mq", matchIfMissing = true)
    public OperationLogSink mqOperationLogSink(org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate) {
        return new MqOperationLogSink(rabbitTemplate);
    }

    /**
     * Fallback 出口：Feign 转发。
     * op-log.sink = feign 时生效；sc-user 内提供 DbOperationLogSink 后此 Bean 不生效。
     */
    @Bean
    @ConditionalOnMissingBean(OperationLogSink.class)
    @ConditionalOnProperty(prefix = "op-log", name = "sink", havingValue = "feign")
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
