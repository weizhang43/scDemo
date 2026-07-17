package com.example.scuser.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.UriComponentsBuilder;

import response.ResponseDto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 客户对话代理：免登录接口，把请求以 SSE 流式透传到 sc-demo-service 的 /stream。
 * 挂在 /user 前缀下复用已验证的网关路由，网关白名单额外放行 /user/chat。
 */
@RestController
@RequestMapping("/user")
public class ChatController {

    private final RestTemplate restTemplate;

    @Value("${scdemo.chat.base-url:http://localhost:9000}")
    private String chatBaseUrl;

    public ChatController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 模型类型
     */
    private static final String CLIENT_TYPE_QIANWEN = "qianwen";

    @GetMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public ResponseEntity<StreamingResponseBody> chat(
            @RequestParam(value = "message", defaultValue = "你好，请介绍一下你自己") String message,
            @RequestParam(value = "clientType", defaultValue = "qianwen") String clientType) {
        //通过前端参数判断后端调用什么模型
        String path = CLIENT_TYPE_QIANWEN.equals(clientType) ? "streamQianwen":"streamDeepseek";
        String url = UriComponentsBuilder.fromHttpUrl(chatBaseUrl)
                .path(path)
                .queryParam("message", message)
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
            // 结束哨兵，前端 EventSource 收到后主动关闭，避免自动重连再次提问
            outputStream.write("data:[DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return null;
        });
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/event-stream;charset=UTF-8"))
                .body(body);
    }

    /**
     * 图片文字识别：接收前端上传的图片，转发到 sc-demo-service 的 /parseImage，
     * 由视觉大模型识别文字后返回。免登录接口，网关白名单放行 /user/parseImage。
     */
    @PostMapping(value = "/parseImage")
    public ResponseDto<String> parseImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseDto.error("请上传图片");
        }
        String url = UriComponentsBuilder.fromHttpUrl(chatBaseUrl)
                .path("parseImage")
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    String name = file.getOriginalFilename();
                    return name != null ? name : "image";
                }
            };
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", resource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(parts, headers);

            String text = restTemplate.postForObject(url, requestEntity, String.class);
            return ResponseDto.success((Object) (text != null ? text : ""));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * 文生图代理：转发到 sc-demo-service 的 /generateImage，返回生成图片的 URL。
     * 免登录接口，网关白名单放行 /user/generateImage。
     */
    @GetMapping(value = "/generateImage")
    public ResponseDto<String> generateImage(
            @RequestParam(value = "message", defaultValue = "生成一张图片") String message) {
        String url = UriComponentsBuilder.fromHttpUrl(chatBaseUrl)
                .path("generateImage")
                .queryParam("message", message)
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        try {
            String imageUrl = restTemplate.getForObject(url, String.class);
            return ResponseDto.success((Object) (imageUrl != null ? imageUrl : ""));
        } catch (Exception e) {
            return ResponseDto.error("图片生成失败：" + e.getMessage());
        }
    }
}
