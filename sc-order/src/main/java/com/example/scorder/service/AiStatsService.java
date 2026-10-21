package com.example.scorder.service;

import com.example.scorder.auth.OrderScope;

import java.util.Map;

/**
 * AI 经营助手统计：评论情感聚合、近7天经营快报。
 */
public interface AiStatsService {

    /**
     * 商家评论情感聚合：取商家全部商品的评价，按内容 md5 缓存逐条分类后聚合计数 + AI 总结。
     */
    Map<String, Object> reviewSentiment(OrderScope scope);

    /**
     * 商家近 7 天经营快报：销售额/订单数/Top5 商品/售后数，供 AI 周报拼 prompt 用。
     */
    Map<String, Object> weeklyBriefing(OrderScope scope);
}
