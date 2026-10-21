package com.example.scorder.service.impl;

import com.example.scorder.auth.OrderScope;
import com.example.scorder.client.AiFeignClient;
import com.example.scorder.entity.ProductReview;
import com.example.scorder.mapper.AfterSaleMapper;
import com.example.scorder.mapper.OrderItemMapper;
import com.example.scorder.mapper.ProductReviewMapper;
import com.example.scorder.service.AiStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 经营助手统计实现：
 * 评论情感：每条评论 Feign 调 sc-user 分类（按内容 md5 缓存），聚合计数 + AI 总结；
 * 周报快报：直接复用既有 OrderItemMapper 聚合方法，组装为 prompt 友好的 Map。
 */
@Slf4j
@Service
public class AiStatsServiceImpl implements AiStatsService {

    /** 单次分析评论上限，超过会拉长时间，Demo 场景直接截断 */
    private static final int SENTIMENT_BATCH_LIMIT = 100;

    @Autowired
    private ProductReviewMapper productReviewMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private AfterSaleMapper afterSaleMapper;

    @Autowired
    private AiFeignClient aiFeignClient;

    @Override
    public Map<String, Object> reviewSentiment(OrderScope scope) {
        if (scope == null || scope.isCustomer()) {
            return errorMap("无权访问");
        }
        Integer merchantId = scope.getMerchantId();
        List<ProductReview> reviews = productReviewMapper.selectByMerchant(merchantId);
        if (reviews.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("total", 0);
            empty.put("positive", 0);
            empty.put("negative", 0);
            empty.put("neutral", 0);
            empty.put("summary", "暂无评论数据");
            empty.put("samples", new ArrayList<>());
            return empty;
        }
        List<ProductReview> sampled = reviews.size() > SENTIMENT_BATCH_LIMIT
                ? reviews.subList(0, SENTIMENT_BATCH_LIMIT) : reviews;
        int positive = 0, negative = 0, neutral = 0;
        List<Map<String, Object>> samples = new ArrayList<>();
        for (ProductReview r : sampled) {
            String label;
            try {
                ResponseDto<String> resp = aiFeignClient.sentiment(r.getContent());
                label = (resp != null && Integer.valueOf(200).equals(resp.getCode()))
                        ? String.valueOf(resp.getDaoResult()) : "neutral";
            } catch (Exception e) {
                log.warn("[AiStats] 情感分类失败 reviewId={}", r.getId(), e);
                label = "neutral";
            }
            if ("positive".equals(label)) positive++;
            else if ("negative".equals(label)) negative++;
            else neutral++;
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.getId());
            item.put("pName", r.getPName());
            item.put("rating", r.getRating());
            item.put("content", r.getContent());
            item.put("label", label);
            samples.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", sampled.size());
        result.put("totalRaw", reviews.size());
        result.put("positive", positive);
        result.put("negative", negative);
        result.put("neutral", neutral);
        result.put("summary", buildSentimentSummary(positive, negative, neutral, sampled.size(), reviews.size()));
        result.put("samples", samples);
        return result;
    }

    @Override
    public Map<String, Object> weeklyBriefing(OrderScope scope) {
        if (scope == null || scope.isCustomer()) {
            return errorMap("无权访问");
        }
        Integer merchantId = scope.getMerchantId();
        Map<String, Object> daily = null;
        try {
            List<Map<String, Object>> list = orderItemMapper.selectDailySalesByMerchant(merchantId, 7);
            if (list != null && !list.isEmpty()) {
                daily = list.get(list.size() - 1);
            }
        } catch (Exception e) {
            log.warn("[AiStats] 拉取近7天趋势失败", e);
        }
        Map<String, Object> today = null;
        try {
            today = orderItemMapper.selectTodayOverviewByMerchant(merchantId);
        } catch (Exception e) {
            log.warn("[AiStats] 拉取今日概览失败", e);
        }
        List<Map<String, Object>> top5 = orderItemMapper.selectSalesRank(5, merchantId);
        // 近7天聚合
        double gmv7 = 0;
        int orders7 = 0;
        try {
            List<Map<String, Object>> all = orderItemMapper.selectDailySalesByMerchant(merchantId, 7);
            if (all != null) {
                for (Map<String, Object> row : all) {
                    Object g = row.get("gmv");
                    Object o = row.get("orderCount");
                    gmv7 += g == null ? 0 : ((Number) g).doubleValue();
                    orders7 += o == null ? 0 : ((Number) o).intValue();
                }
            }
        } catch (Exception e) {
            log.warn("[AiStats] 聚合近7天失败", e);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("todayGmv", today == null ? 0 : today.get("todayGmv"));
        result.put("todayOrders", today == null ? 0 : today.get("todayOrderCount"));
        result.put("weekGmv", gmv7);
        result.put("weekOrders", orders7);
        result.put("dailyTrend", orderItemMapper.selectDailySalesByMerchant(merchantId, 7));
        result.put("topProducts", top5 == null ? new ArrayList<>() : top5.stream()
                .map(row -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("pName", row.get("pName"));
                    item.put("salesCount", row.get("salesCount"));
                    return item;
                }).collect(Collectors.toList()));
        return result;
    }

    private static String buildSentimentSummary(int positive, int negative, int neutral, int sampleSize, int totalRaw) {
        int total = positive + negative + neutral;
        if (total == 0) return "暂无可用评论";
        double posRate = positive * 100.0 / total;
        double negRate = negative * 100.0 / total;
        StringBuilder sb = new StringBuilder();
        sb.append("共分析 ").append(sampleSize).append(" 条评价");
        if (totalRaw > sampleSize) sb.append("（占全部 ").append(totalRaw).append(" 条）");
        sb.append("：好评占比 ").append(String.format("%.0f%%", posRate))
                .append("（").append(positive).append(" 条），");
        sb.append("差评占比 ").append(String.format("%.0f%%", negRate))
                .append("（").append(negative).append(" 条），");
        sb.append("中性 ").append(neutral).append(" 条。");
        if (posRate >= 70) sb.append("整体口碑优秀，建议保持当前商品质量与服务水平。");
        else if (negRate >= 30) sb.append("差评较多，建议优先排查负面评价涉及的商品与售后环节。");
        else sb.append("口碑整体尚可，关注中性评价中可挖掘的改进点。");
        return sb.toString();
    }

    private static Map<String, Object> errorMap(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("error", msg);
        return m;
    }
}
