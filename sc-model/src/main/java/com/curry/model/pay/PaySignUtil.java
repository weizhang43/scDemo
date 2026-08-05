package com.curry.model.pay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 支付网关签名工具（HMAC-SHA256），sc-order 与 sc-pay 两端共用。
 * <p>
 * 签名规则：剔除 sign 与空值参数后，按 key 字典序拼接 k=v&k=v...，
 * 对拼接串做 HmacSHA256，输出小写 hex。timestamp/nonce 也参与签名。
 */
public final class PaySignUtil {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String SIGN_KEY = "sign";

    private PaySignUtil() {}

    /**
     * 对参数集计算签名（不修改入参）。
     */
    public static String sign(Map<String, String> params, String secret) {
        String data = canonicalize(params);
        return hex(hmac(data, secret));
    }

    /**
     * 验签：取出 params 中的 sign 与重算值做常量时间比较。
     */
    public static boolean verify(Map<String, String> params, String secret) {
        if (params == null) {
            return false;
        }
        String given = params.get(SIGN_KEY);
        if (given == null || given.isEmpty()) {
            return false;
        }
        String expected = sign(params, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                given.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private static String canonicalize(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                if (k == null || SIGN_KEY.equals(k) || v == null || v.isEmpty()) {
                    continue;
                }
                sorted.put(k, v);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private static byte[] hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
