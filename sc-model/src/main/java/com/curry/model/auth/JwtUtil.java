package com.curry.model.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 轻量级 JWT 工具（HS256）。
 * <p>
 * 设计取舍：刻意不引入 jjwt 依赖，避免 sc-model 被重度依赖污染；
 * 仅做签名 + 校验，不做加密，敏感字段不要直接放入 payload。
 */
public final class JwtUtil {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 密钥：生产环境应改为从配置中心读取并定期轮换 */
    private static String secret = "scDemo-default-secret-please-change-it-in-nacos-2026";

    private JwtUtil() {}

    /**
     * 设置 HS256 签名密钥，仅接受非空非空白字符串。生产环境应从配置中心读取并定期轮换。
     */
    public static void setSecret(String s) {
        if (s != null && !s.trim().isEmpty()) {
            secret = s;
        }
    }

    /**
     * 根据登录用户信息生成 HS256 签名的 JWT，payload 含 uId/uName/realName/iat。
     * @param user 登录用户上下文
     * @return 三段式 JWT 字符串
     * @throws IllegalStateException 生成或签名过程出错时抛出
     */
    public static String generate(LoginUser user) {
        try {
            Map<String, Object> header = new HashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new HashMap<>();
            payload.put("uId", user.getUId());
            payload.put("uName", user.getUName());
            payload.put("realName", user.getRealName());
            payload.put("iat", System.currentTimeMillis() / 1000L);

            String h = base64Url(encode(header));
            String p = base64Url(encode(payload));
            String data = h + "." + p;
            String sig = base64Url(sign(data));
            return data + "." + sig;
        } catch (Exception e) {
            throw new IllegalStateException("生成 JWT 失败", e);
        }
    }

    /**
     * 校验签名并返回 payload，失败返回 null
     */
    public static Map<String, Object> verify(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        String data = parts[0] + "." + parts[1];
        String expectedSig = base64Url(sign(data));
        if (!expectedSig.equals(parts[2])) {
            return null;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] encode(Map<String, Object> map) throws Exception {
        return MAPPER.writeValueAsBytes(map);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }
}
