package com.example.scorder.mq;

/**
 * sc-order 侧支付通知相关 RocketMQ 常量。
 *
 * 与 sc-pay 的 com.example.scpay.mq.PayNotifyMqConst 必须保持一致：
 *  - Topic/Tag 字符串必须完全相同，否则消费方订阅不到生产方的消息；
 *  - 消费组只需在 sc-order 一侧定义（生产组在 sc-pay 一侧）。
 *
 * 两边各自定义常量而不共享 com.curry.model 是为了：
 *  - 避免引入"为共享而共享"的额外抽象层；
 *  - 各模块对自己的 MQ 常量有清晰所有权，便于独立演进；
 *  - 字符串字面量通过 code review 保证一致。
 */
public final class PayNotifyMqConst {

    private PayNotifyMqConst() {
    }

    /** 支付通知 Topic：与 sc-pay 一致 */
    public static final String TOPIC_PAY_NOTIFY = "pay_notify_topic";

    /** 支付结果通知 Tag：与 sc-pay 一致 */
    public static final String TAG_PAY_RESULT = "PAY_RESULT";

    /** sc-order 消费组：集群模式，多实例分摊消费不重复 */
    public static final String CONSUMER_GROUP_PAY_NOTIFY = "pay-notify-consumer-group";

    /** 消费失败最大重试次数：超过后消息进入死信队列 */
    public static final int MAX_RECONSUME_TIMES = 5;
}
