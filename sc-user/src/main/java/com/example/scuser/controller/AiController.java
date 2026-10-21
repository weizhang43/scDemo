package com.example.scuser.controller;

import com.curry.model.auth.AuthConstant;
import com.example.scuser.service.AiSentimentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import response.ResponseDto;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * AI 经营助手接口：
 * - /inner/sentiment 服务间单条情感分类（按内容 md5 缓存）；
 * - /chat/guide 智能导购 SSE 流（先按消息匹配候选商品 top5 → 拼 prompt → 透传 Python 服务）；
 * - /chat/weeklyReport AI 经营周报 SSE 流（先聚合近7天数据拼 prompt 再透传）。
 * /inner/sentiment 用内部令牌鉴权，导购/周报走登录态（uId 取网关注入头）。
 */
@RestController
@RequestMapping("/user")
public class AiController {

    @Autowired
    private AiSentimentService aiSentimentService;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${scdemo.chat.base-url:http://localhost:9000}")
    private String chatBaseUrl;

    @Value("${inner.auth.token:}")
    private String innerToken;

    /** 内部：单条情感分类（sc-order 经 AiFeignClient 调用） */
    @PostMapping("/ai/inner/sentiment")
    public ResponseDto<String> sentiment(@RequestParam("content") String content,
                                          @RequestHeader(value = AuthConstant.HEADER_X_INNER_TOKEN, required = false) String inner,
                                          @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer callerUId) {
        boolean innerOk = innerToken != null && !innerToken.isEmpty() && innerToken.equals(inner);
        if (!innerOk && callerUId == null) {
            return ResponseDto.error("无权限");
        }
        return ResponseDto.success(aiSentimentService.classify(content));
    }

    /**
     * 智能导购 SSE：先把消息作为关键词调 sc-product /product/search/suggest 召回 top5 候选，
     * 拼成 prompt（约定输出含 [P:pId:商品名] 标记）后透传 Python 服务。
     * 走登录态，uId 取网关注入头（仅用于校验登录，不进 prompt）。
     */
    @GetMapping(value = "/chat/guide", produces = "text/event-stream;charset=UTF-8")
    public ResponseEntity<StreamingResponseBody> guide(
            @RequestParam(value = "message", defaultValue = "你好") String message,
            @RequestParam(value = "clientType", defaultValue = "qianwen") String clientType,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        if (uId == null) {
            return unauthorized();
        }
        String prompt = buildGuidePrompt(message);
        return streamToPython(prompt, clientType);
    }

    /**
     * AI 经营周报 SSE：拉取近7天商家快报拼成 prompt 后透传。
     * 周报数据来源在 sc-order，sc-user 通过 RestTemplate 直接调网关（localhost:8000/order/ai/weekly-briefing）。
     */
    @GetMapping(value = "/chat/weeklyReport", produces = "text/event-stream;charset=UTF-8")
    public ResponseEntity<StreamingResponseBody> weeklyReport(
            @RequestParam(value = "clientType", defaultValue = "qianwen") String clientType,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType,
            @RequestHeader(value = AuthConstant.HEADER_AUTHORIZATION, required = false) String auth) {
        if (uId == null) {
            return unauthorized();
        }
        // 顾客不允许调周报：商家专属经营数据
        if (uType != null && uType == AuthConstant.U_TYPE_CUSTOMER) {
            return unauthorized();
        }
        String briefing = fetchWeeklyBriefing(auth);
        String prompt = "你是经营助手。根据下面的近 7 天经营快报写一份给商家的周报草稿，分「数据概览/亮点/关注点/建议」四段，中文，200 字内。\n" + briefing;
        return streamToPython(prompt, clientType);
    }

    /**
     * 构建导购 prompt：把候选商品（名称+价格+ID）拼进 instruction，约定模型输出商品标记。
     */
    private String buildGuidePrompt(String message) {
        String suggestions = searchSuggestList(message);
        StringBuilder sb = new StringBuilder();
        sb.append("你是商城导购助手。用户问题：「").append(message).append("」。\n");
        if (suggestions == null || suggestions.isEmpty()) {
            sb.append("当前没有候选商品匹配。请按用户问题做一般性导购建议，不要捏造商品。\n");
        } else {
            sb.append("候选商品（仅可在回答中引用这些）：\n").append(suggestions).append("\n");
            sb.append("如需推荐上述商品，必须用约定标记 [P:商品ID:商品名] 形式列出（一行一个），便于前端解析为可点击卡片。\n");
        }
        sb.append("回答用中文，简洁、有温度。");
        return sb.toString();
    }

    /**
     * 调 sc-product /product/search/suggest，把结果拼成「pId, pName, price」多行文本。
     */
    private String searchSuggestList(String keyword) {
        String url = UriComponentsBuilder.fromHttpUrl("http://localhost:8002/sc-product")
                .path("/product/search/suggest")
                .queryParam("keyword", keyword)
                .queryParam("limit", 5)
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        try {
            String body = restTemplate.getForObject(url, String.class);
            return extractSuggestText(body);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 ResponseDto JSON 中粗略提取 pId/pName/price 三个字段拼行 */
    private static String extractSuggestText(String json) {
        if (json == null || json.isEmpty()) return null;
        // 简易解析：避免引入 Jackson 类型绑定，Demo 用正则抓字段
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\\{\"pId\":(\\d+),\"pName\":\"([^\"]*)\",\"price\":([0-9.]+)").matcher(json);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            sb.append("- ID=").append(m.group(1))
                    .append(" 名称=").append(m.group(2))
                    .append(" 价格=").append(m.group(3)).append('\n');
        }
        return sb.toString();
    }

    /**
     * 调 sc-order /order/ai/weekly-briefing 拿快报，转成 prompt 友好的文本。
     */
    private String fetchWeeklyBriefing(String auth) {
        String url = "http://localhost:8000/order/ai/weekly-briefing";
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (auth != null && !auth.isEmpty()) {
                headers.set(AuthConstant.HEADER_AUTHORIZATION, auth);
            }
            headers.set(AuthConstant.HEADER_X_INNER_TOKEN, innerToken);
            org.springframework.http.HttpEntity<Void> req = new org.springframework.http.HttpEntity<>(headers);
            String body = restTemplate.exchange(url, HttpMethod.GET, req, String.class).getBody();
            return body == null ? "无快报数据" : body;
        } catch (Exception e) {
            return "拉取近7天快报失败：" + e.getMessage();
        }
    }

    /**
     * 把 prompt 透传到 Python 服务的 /streamQianwen|/streamDeepseek，输出尾部补 [DONE] 哨兵。
     */
    private ResponseEntity<StreamingResponseBody> streamToPython(String prompt, String clientType) {
        String path = "qianwen".equals(clientType) ? "streamQianwen" : "streamDeepseek";
        String url = UriComponentsBuilder.fromHttpUrl(chatBaseUrl)
                .path(path)
                .queryParam("message", prompt)
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        StreamingResponseBody body = outputStream -> restTemplate.execute(url, HttpMethod.GET, null, response -> {
            try (InputStream in = response.getBody()) {
                byte[] buffer = new byte[512];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                    outputStream.flush();
                }
            }
            outputStream.write("data:[DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return null;
        });
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/event-stream;charset=UTF-8"))
                .body(body);
    }

    private static ResponseEntity<StreamingResponseBody> unauthorized() {
        return ResponseEntity.status(401).body(out -> out.write("data:[DONE]\n\n".getBytes(StandardCharsets.UTF_8)));
    }
}
