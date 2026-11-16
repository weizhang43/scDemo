package com.example.scpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 模拟支付网关配置项。
 *
 * 改造说明：
 *  - 原本通过 notifyScheduler() Bean 提供 ThreadPoolTaskScheduler，
 *    供 NotifySender 自建重试调度使用；
 *  - 接入 RocketMQ 后，重试由 broker 自动按延迟级别递增完成，
 *    notifyScheduler 不再需要，删除 Bean 定义避免无用户线程池。
 *  - notifyDelayMs 字段保留但语义变化：原值用于调度器延迟，
 *    现仅作为参考（实际延迟由 PayNotifyMqConst.DELAY_LEVEL_FIRST_SEND 决定）。
 *    保留字段是为了不破坏既有配置文件兼容性。
 */
@Configuration
@ConfigurationProperties(prefix = "mock-pay")
public class MockPayProperties {

    /** 支付结果确定后延迟多久发起异步回调（毫秒，参考值，实际延迟由 RocketMQ 延迟级别决定） */
    private long notifyDelayMs = 1000;

    /** 是否故意重复发送回调（演示商户侧幂等） */
    private boolean notifyDuplicate = false;

    public long getNotifyDelayMs() {
        return notifyDelayMs;
    }

    public void setNotifyDelayMs(long notifyDelayMs) {
        this.notifyDelayMs = notifyDelayMs;
    }

    public boolean isNotifyDuplicate() {
        return notifyDuplicate;
    }

    public void setNotifyDuplicate(boolean notifyDuplicate) {
        this.notifyDuplicate = notifyDuplicate;
    }
}
