package com.example.scuser.service;

/**
 * AI 内部辅助接口（sc-order 经 Feign 调用）：单条情感分类。
 * sc-user 已有 ChatController 透传 Python 服务的能力，情感分类复用同样链路。
 */
public interface AiSentimentService {

    /**
     * 单条文本情感分类：返回 positive/negative/neutral。
     * 同内容命中 Redis 缓存（key 为 md5），缓存缺失时调 Python 服务一次性分类。
     */
    String classify(String content);
}
