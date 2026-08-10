package com.curry.model.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * 把配置中心（Nacos）下发的 JWT 签名密钥注入静态工具类 {@link JwtUtil}。
 * <p>
 * 配置项为 {@code sc.jwt.secret}，按 Spring 松散绑定规则也可用环境变量 {@code SC_JWT_SECRET} 覆盖；
 * 未配置时不做注入，由 {@link JwtUtil} 在首次签发/校验时抛异常提示，避免密钥落在源码里。
 */
@Configuration
public class JwtSecretAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtSecretAutoConfiguration.class);

    @Value("${sc.jwt.secret:}")
    private String secret;

    /**
     * 容器启动后交付密钥，保证签发方（sc-user）与校验方（sc-gateway）共用同一份配置。
     */
    @PostConstruct
    public void applySecret() {
        if (secret == null || secret.trim().isEmpty()) {
            LOGGER.warn("[JwtSecret] 未配置 sc.jwt.secret，JWT 签发与校验将在首次调用时失败");
            return;
        }
        JwtUtil.setSecret(secret);
        LOGGER.info("[JwtSecret] 已从配置中心注入 JWT 密钥");
    }
}
