package com.example.scorder.mq;

import com.example.scorder.config.RabbitMqConfig;
import com.example.scorder.dto.SeckillRequest;
import com.example.scorder.service.OrderFeignService;
import com.example.scorder.service.impl.SeckillOrderHelper;
import com.example.scorder.vo.SeckillResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 基于 RabbitMQ 的秒杀订单生产者（替换原 RocketMQ 实现）。
 *
 * 为什么从 RocketMQ 迁回 RabbitMQ：
 *  1. 系统收敛：sc-order 已经用 RabbitMQ 处理订单邮件通知和超时取消，
 *     单 MQ 维护一套 broker、监控、配置即可，避免双 MQ 共存的运维负担。
 *  2. 能力足够：秒杀链路核心诉求是"异步落库 + 失败重试 + 死信兜底"，
 *     RabbitMQ 的 ACK + retry + DLX 全部支持，吞吐也能扛到 1w+ QPS。
 *  3. RocketMQ 的优势用不上：顺序消息（秒杀单用户单条消息无需顺序）、
 *     事务消息（投递失败回滚 Redis 已兜底）、10w+ 吞吐（demo 无真实流量）。
 *
 * 投递方式：同步 convertAndSend（带 publisher-confirm）。
 * 不用 asyncSend：RabbitMQ 的 channel.send 本身就是非阻塞的，
 * 与 RocketMQ 的同步 syncSend 不同，不会卡住秒杀接口的同步段。
 *
 * 失败兜底：publisher-confirm 类型为 correlated，但本方法不注册回调（保持简单）。
 * broker 不可用时 convertAndSend 会抛 AmqpException，由调用方 OrderServiceImpl.seckill
 * 的 try/catch 兜底回滚 Redis 预扣。
 */
@Component
@Primary // 多实现时优先注入本类（虽然现在只剩这一个实现，注解保留便于扩展）
public class RabbitMqSeckillOrderProducer implements SeckillOrderProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqSeckillOrderProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderFeignService orderFeignService;

    @Autowired
    private SeckillOrderHelper seckillOrderHelper;

    /**
     * 投递秒杀下单消息到 RabbitMQ 的 queue.seckill.order。
     *
     * 投递失败处理：broker 不可用时 convertAndSend 抛 AmqpException，
     * 此处捕获后执行补偿（回滚 Redis 预扣 + 写 FAILED），保证用户不卡在 PENDING。
     * 补偿前先 readResult 检查状态，避免覆盖并发场景下已被消费出的 SUCCESS/FAILED。
     *
     * @param message 秒杀请求（含 uId/activityId/addressId/addPerson）
     */
    @Override
    public void send(SeckillRequest message) {
        Integer uId = message.getuId();
        Integer activityId = message.getActivityId();
        try {
            // RabbitMqConfig 已注册 Jackson2JsonMessageConverter，
            // SeckillRequest 会被序列化为 JSON，消费端按同类型反序列化。
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EXCHANGE_DIRECT,
                    RabbitMqConfig.ROUTING_KEY_SECKILL_ORDER,
                    message
            );
            LOGGER.info("[seckill-producer] 投递成功 uId={}, activityId={}", uId, activityId);
        } catch (Exception e) {
            // broker 不可用、网络故障等导致投递失败。
            // 此时 Redis 预扣已成功但消息没入队，用户会卡在 PENDING。
            // 补偿：回滚 Redis 预扣（归还名额，让用户能重试）+ 写 FAILED 结果供前端轮询读到终态。
            LOGGER.error("[seckill-producer] 投递失败，执行补偿 uId={}, activityId={}", uId, activityId, e);
            handleSendFailure(message);
        }
    }

    /**
     * 投递失败补偿：仅当 Redis 结果仍是 PENDING 时执行（避免覆盖已被消费出的 SUCCESS/FAILED）。
     * 步骤：1. 回滚 Redis 预扣（归还名额）2. 写 FAILED 结果供前端轮询读到终态。
     *
     * 边界场景：理论上 RabbitMQ 的 publisher-confirm 可能延迟到消费后才回调，
     * 但 convertAndSend 抛异常的场景是"channel 未建立/发送即失败"，不会出现"消息已消费但发送方报错"。
     * 这里的 readResult 检查仍是 defense in depth，应对极端并发场景。
     */
    private void handleSendFailure(SeckillRequest message) {
        Integer uId = message.getuId();
        Integer activityId = message.getActivityId();
        try {
            SeckillResultVO existing = seckillOrderHelper.readResult(uId, activityId);
            if (existing != null && !"PENDING".equals(existing.getStatus())) {
                LOGGER.warn("[seckill-producer] 补偿跳过，结果已非 PENDING uId={}, activityId={}, status={}",
                        uId, activityId, existing.getStatus());
                return;
            }
            // 回滚预扣：restoreStock=true 归还名额（投递失败不是库存问题，名额该归还）
            orderFeignService.rollbackSeckillStock(activityId, uId, true);
            // 写 FAILED 终态，前端轮询能立即拿到结果而不是卡 PENDING
            seckillOrderHelper.saveResult(uId, activityId,
                    new SeckillResultVO("FAILED", "系统繁忙，请重试", null));
        } catch (Exception ex) {
            // 补偿失败只能记日志：调用方 OrderServiceImpl.seckill 已经返回 PENDING 给用户，
            // 这里再抛异常会污染调用栈，吞掉避免影响秒杀接口返回。
            LOGGER.error("[seckill-producer] 补偿失败 uId={}, activityId={}", uId, activityId, ex);
        }
    }
}
