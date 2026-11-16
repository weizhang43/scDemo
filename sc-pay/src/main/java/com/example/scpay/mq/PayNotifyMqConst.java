package com.example.scpay.mq;

/**
 * RocketMQ 支付通知相关常量集中定义。
 *
 * 设计目标：
 *  - sc-pay 是生产者：支付成功/失败后投递消息到 broker，不再自建重试调度器；
 *  - sc-order 是消费者：监听同一 Topic，调用现有 handleNotify 完成业务处理；
 *  - 用 RocketMQ 的延迟级别替代原 [5s,15s,60s] 自建重试矩阵。
 *
 * Topic/Tag 命名约定与 sc-order 内部 RabbitMqConst/RocketMqConst 风格一致：
 *  - Topic 按业务域命名，Tag 做二级分类。
 */
public final class PayNotifyMqConst {

    private PayNotifyMqConst() {
    }

    /** 支付通知 Topic：sc-pay 投递，sc-order 消费 */
    public static final String TOPIC_PAY_NOTIFY = "pay_notify_topic";

    /** 支付结果通知 Tag：与未来退款通知等区分 */
    public static final String TAG_PAY_RESULT = "PAY_RESULT";

    /** sc-pay 生产组：仅用于 broker 侧统计 */
    public static final String PRODUCER_GROUP_PAY_NOTIFY = "pay-notify-producer-group";

    /** sc-order 消费组：集群模式，多实例分摊消费不重复 */
    public static final String CONSUMER_GROUP_PAY_NOTIFY = "pay-notify-consumer-group";

    /** 消息 key 前缀：pay_{transactionId}，控制台按交易号反查消息 */
    public static final String MSG_KEY_PREFIX = "pay_";

    /**
     * RocketMQ 延迟级别（开源版固定 18 级）：
     *  1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     *  下标从 1 开始，0 表示不延迟（立即投递）。
     *
     * 业务对应：
     *  - 首发延迟 1s：对应原 properties.notifyDelayMs=1000，用 level=1 实现
     *  - 重试间隔：消费失败时 RocketMQ 自动按 1s/5s/10s/30s/1m…递增重试，
     *    替代原手写的 [5s,15s,60s] 三次固定间隔
     */
    public static final int DELAY_LEVEL_FIRST_SEND = 1; // 1s

    /**
     * 拼接带 Tag 的 destination：RocketMQ Spring 客户端约定格式 "topic:tag"。
     */
    public static String payNotifyDestination() {
        return TOPIC_PAY_NOTIFY + ":" + TAG_PAY_RESULT;
    }

    /**
     * 生成消息业务 key：pay_{transactionId}。
     * 同一交易号在 broker 上唯一（结合 Tag），便于按交易号排障。
     */
    public static String payNotifyMsgKey(String transactionId) {
        return MSG_KEY_PREFIX + transactionId;
    }
}
