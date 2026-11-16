package com.example.scorder.mq;

import com.example.scorder.config.RabbitMqConfig;
import com.example.scorder.dto.SeckillRequest;
import com.example.scorder.service.OrderService;
import com.example.scorder.service.impl.SeckillOrderHelper;
import com.example.scorder.vo.SeckillResultVO;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀下单消息消费者：监听 RabbitMQ 的 queue.seckill.order，收到消息后调用 OrderService 落库。
 *
 * 与 OrderMqListener 的邮件消费者保持一致的 ACK 模式：
 *  - acknowledge-mode: manual（配置在 application-mq.yml），必须手动 ACK，
 *    否则消息滞留 unacked，连接断开/重启后被重投导致重复消费。
 *  - retry.max-attempts: 3（配置在 application-mq.yml），消费抛异常时 Spring AMQP 自动重试 3 次。
 *  - default-requeue-rejected: false（配置在 application-mq.yml），
 *    重试 3 次仍失败后不 requeue，消息死信到 dlx_queue（由队列 x-dead-letter-exchange 指定）。
 *
 * 幂等设计：RabbitMQ 集群模式至少投递一次（at-least-once），重试或 ACK 丢失可能重复消费。
 * 消费前检查 Redis 结果状态，非 PENDING 直接跳过，避免重复落库（SeckillOrderHelper.process 已写终态）。
 *
 * 顺序性说明：RabbitMQ 没有按 hash key 路由到分片的内置能力，单队列串行消费。
 * 秒杀场景单用户单条消息，无需顺序保证；若未来需要同用户多消息有序，
 * 可改为多队列 + 一致性 hash 交换机插件，但当前不需要。
 */
@Component
public class SeckillOrderMqListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillOrderMqListener.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private SeckillOrderHelper seckillOrderHelper;

    /**
     * 消费秒杀下单消息：幂等检查 → 调用 processSeckillOrder（@GlobalTransactional Seata 全局事务）。
     *
     * ACK 策略：
     *  - 正常消费（不论 processSeckillOrder 成功还是抛业务异常）→ basicAck，
     *    业务失败已在 SeckillOrderHelper.compensateAndRethrow 内补偿（写 FAILED + 回滚 Redis），
     *    这里不该 requeue 让消息死循环重投。
     *  - 抛非业务异常（如 NPE、序列化失败）→ basicNack(requeue=false)，
     *    触发 retry 重试 3 次，仍失败则死信到 dlx_queue。
     *
     * 注意：retry 配置下，业务异常实际会被 Spring AMQP 拦截重试，
     * 业务异常本身在补偿后已写 FAILED 终态，下次重试进来幂等检查会直接跳过。
     * 这里 catch 业务异常后直接 ACK 是"快速失败"路径，避免无意义的重试消耗。
     *
     * @param message     秒杀请求（由 Jackson2JsonMessageConverter 从 JSON 反序列化得到）
     * @param channel     RabbitMQ Channel，用于手动 ACK
     * @param deliveryTag 消息投递标签，ACK/NACK 时回传
     */
    @RabbitListener(queues = RabbitMqConfig.QUEUE_SECKILL_ORDER)
    public void onMessage(SeckillRequest message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Integer uId = message.getuId();
        Integer activityId = message.getActivityId();
        LOGGER.info("[seckill-consumer] 收到消息 uId={}, activityId={}", uId, activityId);

        try {
            // 幂等检查：重试或重复投递时，Redis 结果已为终态（SUCCESS/FAILED）则直接跳过。
            // SeckillOrderHelper.process 失败时会写 FAILED 并抛 BusinessException，
            // 因此重试进来时一定读到 FAILED，跳过 → 不会重复落库也不会重复启动 Seata 全局事务。
            SeckillResultVO existing = seckillOrderHelper.readResult(uId, activityId);
            if (existing != null && !"PENDING".equals(existing.getStatus())) {
                LOGGER.warn("[seckill-consumer] 重复消费，跳过 uId={}, activityId={}, status={}",
                        uId, activityId, existing.getStatus());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 调用业务方法：内部 @GlobalTransactional 包 Seata 全局事务，
            // 失败时 SeckillOrderHelper.compensateAndRethrow 已写 FAILED + 回滚 Redis 预扣，
            // 并抛 BusinessException 触发 Seata 全局回滚订单与真实库存。
            orderService.processSeckillOrder(message);
            LOGGER.info("[seckill-consumer] 消费完成 uId={}, activityId={}", uId, activityId);
            // 业务成功或业务异常（已补偿）都 ACK，避免毒消息死循环
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 非业务异常（如序列化失败、NullPointer）→ NACK 不 requeue，
            // 触发 retry 重试 3 次（application-mq.yml 配置），仍失败则死信到 dlx_queue。
            // 死信队列由 OrderMqListener.handleTimeoutOrder 同队列（dlx_queue）承载，
            // 后续可加专门的死信监控（或扩展独立死信队列）。
            LOGGER.error("[seckill-consumer] 消费异常 uId={}, activityId={}", uId, activityId, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
