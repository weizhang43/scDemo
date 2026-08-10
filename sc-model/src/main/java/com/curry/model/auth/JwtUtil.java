package com.curry.model.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtUtil.class);

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 密钥配置项：优先读系统属性，其次读环境变量 */
    private static final String SECRET_CONFIG_KEY = "SC_JWT_SECRET";

    /** JWT 三段式结构的段数 */
    private static final int JWT_PARTS = 3;

    /** 毫秒转秒 */
    private static final long MILLIS_PER_SECOND = 1000L;

    /**
     * 密钥：不在源码中保留默认值，从系统属性/环境变量 SC_JWT_SECRET 读取，
     * 或由应用启动时通过 {@link #setSecret(String)} 从配置中心注入并定期轮换。
     */
    private static String secret = resolveConfiguredSecret();

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
            payload.put("iat", System.currentTimeMillis() / MILLIS_PER_SECOND);

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
        Map<String, Object> payload = null;
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length == JWT_PARTS && isSignatureValid(parts)) {
            payload = parsePayload(parts[1]);
        }
        return payload;
    }

    /**
     * 比对第三段签名与按前两段重新计算的签名是否一致。
     */
    private static boolean isSignatureValid(String[] parts) {
        String expectedSig = base64Url(sign(parts[0] + "." + parts[1]));
        return expectedSig.equals(parts[2]);
    }

    /**
     * 解析 Base64Url 编码的 payload 段，格式非法时返回 null。
     */
    private static Map<String, Object> parsePayload(String payloadPart) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(payloadPart);
            return MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            LOGGER.warn("[JwtUtil] 解析 JWT payload 失败", e);
            return null;
        }
    }

    /**
     * 启动时从系统属性/环境变量解析密钥，均未配置时返回 null（首次使用时报错提示）。
     */
    private static String resolveConfiguredSecret() {
        String configured = System.getProperty(SECRET_CONFIG_KEY);
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv(SECRET_CONFIG_KEY);
        }
        return configured;
    }

    /**
     * 获取当前密钥，未配置时抛出异常并说明配置方式。
     */
    private static String requireSecret() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT 密钥未配置：请设置系统属性/环境变量 " + SECRET_CONFIG_KEY
                            + "，或在应用启动时调用 JwtUtil.setSecret 注入配置中心的密钥");
        }
        return secret;
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
            mac.init(new SecretKeySpec(requireSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }
}
