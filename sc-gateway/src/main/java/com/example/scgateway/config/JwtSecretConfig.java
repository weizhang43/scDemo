package com.example.scgateway.config;

import com.curry.model.auth.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * 启动时把 Spring 配置（环境变量/Nacos）中的 SC_JWT_SECRET 注入 JwtUtil。
 * JwtUtil 是 sc-model 里的纯静态工具类，无法直接走 Spring 注入；
 * 必须与 sc-user 配置同一密钥，否则网关验签失败。
 */
@Configuration
public class JwtSecretConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtSecretConfig.class);

    @Value("${SC_JWT_SECRET:}")
    private String secret;

    @PostConstruct
    public void injectSecret() {
        if (secret == null || secret.trim().isEmpty()) {
            LOGGER.warn("[JwtSecretConfig] SC_JWT_SECRET 未配置，网关验签将放行失败");
            return;
        }
        JwtUtil.setSecret(secret);
        LOGGER.info("[JwtSecretConfig] 已注入 JwtUtil 密钥");
    }
}
