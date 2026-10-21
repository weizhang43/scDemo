package com.example.scorder.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import response.ResponseDto;

/**
 * AI 服务间客户端（sc-user /user/ai/inner/*）。
 * sc-order 评论情感分析经此调用 sc-user 与 Python 服务的链路。
 */
@Component
@FeignClient(value = "sc-user", contextId = "aiFeignClient", path = "/sc-user")
public interface AiFeignClient {

    /**
     * 单条评论情感分类：返回 positive/negative/neutral。
     * 结果在 sc-user 侧按内容 md5 缓存 Redis，重复内容秒回。
     */
    @PostMapping("/user/ai/inner/sentiment")
    ResponseDto<String> sentiment(@RequestParam("content") String content);
}
