package com.curry.scjob.exception;

/**
 * 定时任务执行异常。
 * 用于替代裸抛 RuntimeException：Job 调用下游 Feign 接口失败或返回非成功码时抛出，
 * 由 XXL-Job 框架捕获并将本次调度标记为失败，触发告警/重试策略。
 */
public class JobExecuteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 仅携带失败描述的构造器（如下游返回非成功码）。
     *
     * @param message 失败描述
     */
    public JobExecuteException(String message) {
        super(message);
    }

    /**
     * 携带失败描述与原始异常的构造器（如 Feign 调用抛出异常）。
     *
     * @param message 失败描述
     * @param cause   原始异常
     */
    public JobExecuteException(String message, Throwable cause) {
        super(message, cause);
    }
}
