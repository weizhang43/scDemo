package com.example.scuser.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码哈希工具：BCrypt 加盐哈希与校验。
 * <p>
 * 替代早期明文存储/比对，密码摘要长度固定 60 字符，盐内嵌于摘要。
 */
public final class PasswordUtil {

    /** BCrypt cost factor，与 spring-security 默认值一致，兼顾安全与性能 */
    private static final int STRENGTH = 10;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(STRENGTH);

    private PasswordUtil() {
    }

    /**
     * 对原始密码做 BCrypt 加盐哈希，返回 60 字符摘要。
     */
    public static String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验原始密码与摘要是否匹配；摘要或原始密码为空时返回 false。
     */
    public static boolean matches(String rawPassword, String hashedPassword) {
        if (rawPassword == null || rawPassword.isEmpty()
                || hashedPassword == null || hashedPassword.isEmpty()) {
            return false;
        }
        return ENCODER.matches(rawPassword, hashedPassword);
    }
}
