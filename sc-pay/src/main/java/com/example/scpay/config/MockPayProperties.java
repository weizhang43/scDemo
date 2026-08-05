package com.example.scpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConfigurationProperties(prefix = "mock-pay")
public class MockPayProperties {

    /** 支付结果确定后延迟多久发起异步回调（毫秒） */
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

    @Bean
    public ThreadPoolTaskScheduler notifyScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("pay-notify-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
