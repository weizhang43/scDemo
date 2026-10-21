package com.example.scorder.controller;

import com.example.scorder.auth.OrderScope;
import com.example.scorder.auth.OrderScopeResolver;
import com.example.scorder.service.AiStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

import java.util.Map;

/**
 * AI 经营助手统计接口（商家/管理员）：
 * /order/ai/review-sentiment 评论情感聚合；/order/ai/weekly-briefing 近7天快报（拼 AI 周报 prompt 用）。
 */
@RestController
@RequestMapping("/order/ai")
public class AiStatsController {

    @Autowired
    private AiStatsService aiStatsService;

    @GetMapping("/review-sentiment")
    public ResponseDto<Object> reviewSentiment() {
        OrderScope scope = OrderScopeResolver.current();
        if (scope.isCustomer()) {
            return ResponseDto.error("无权访问");
        }
        return ResponseDto.success(aiStatsService.reviewSentiment(scope));
    }

    @GetMapping("/weekly-briefing")
    public ResponseDto<Map<String, Object>> weeklyBriefing() {
        OrderScope scope = OrderScopeResolver.current();
        if (scope.isCustomer()) {
            return ResponseDto.error("无权访问");
        }
        return ResponseDto.success(aiStatsService.weeklyBriefing(scope));
    }
}
