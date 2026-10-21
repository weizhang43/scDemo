package com.example.scuser.service.impl;

import com.example.scuser.service.AiSentimentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * 单条情感分类实现：调用 Python 服务 /sentiment（同 streamQianwen 路径风格的轻量分类接口）。
 * 结果按 md5(content) 缓存 Redis（TTL 7 天），评论内容不变不重复调用 LLM。
 */
@Slf4j
@Service
public class AiSentimentServiceImpl implements AiSentimentService {

    private static final String CACHE_KEY_PREFIX = "ai:review:sentiment:";

    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private static final String DEFAULT_LABEL = "neutral";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${scdemo.chat.base-url:http://localhost:9000}")
    private String chatBaseUrl;

    @Override
    public String classify(String content) {
        if (content == null || content.trim().isEmpty()) {
            return DEFAULT_LABEL;
        }
        String key = CACHE_KEY_PREFIX + md5(content.trim());
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        String label = callRemote(content.trim());
        try {
            stringRedisTemplate.opsForValue().set(key, label, CACHE_TTL);
        } catch (Exception e) {
            log.warn("[Sentiment] 缓存写入失败 content={}... key={}", content.substring(0, Math.min(8, content.length())), key, e);
        }
        return label;
    }

    /**
     * 调 Python 服务的 sentiment 接口（同步返回 positive/negative/neutral）。
     * 接口不存在或调用失败时降级为 neutral，不阻断商家分析流程。
     */
    private String callRemote(String content) {
        String url = UriComponentsBuilder.fromHttpUrl(chatBaseUrl)
                .path("sentiment")
                .queryParam("message", content)
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        try {
            String resp = restTemplate.getForObject(url, String.class);
            if (resp == null) {
                return DEFAULT_LABEL;
            }
            String normalized = resp.trim().toLowerCase();
            if (normalized.contains("positive")) return "positive";
            if (normalized.contains("negative")) return "negative";
            if (normalized.contains("neutral")) return "neutral";
            // 兜底：返回原文作为中立
            return DEFAULT_LABEL;
        } catch (Exception e) {
            log.warn("[Sentiment] 调用失败 content={}...", content.substring(0, Math.min(8, content.length())), e);
            return DEFAULT_LABEL;
        }
    }

    private static String md5(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xf, 16));
                sb.append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
