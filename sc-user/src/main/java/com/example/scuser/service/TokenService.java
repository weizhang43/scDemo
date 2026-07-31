package com.example.scuser.service;

import com.curry.model.auth.AuthConstant;
import com.curry.model.auth.JwtUtil;
import com.curry.model.auth.LoginUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 登录 Token 的存储与校验。
 * 用 StringRedisTemplate 直接存 JSON 字符串，避免跨服务对象反序列化歧义。
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String issue(LoginUser user) {
        String token = JwtUtil.generate(user);
        String key = AuthConstant.buildTokenKey(user.getUId(), token);

        Map<String, Object> data = new HashMap<>();
        data.put("uId", user.getUId());
        data.put("uName", user.getUName());
        data.put("realName", user.getRealName());
        data.put("uType", user.getUType());

        try {
            String json = objectMapper.writeValueAsString(data);
            stringRedisTemplate.opsForValue().set(key, json,
                    AuthConstant.DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("[TokenService] 写入 Redis 成功 key={} json={}", key, json);
        } catch (Exception e) {
            log.error("[TokenService] 写入 Redis 失败 key={}", key, e);
            throw new IllegalStateException("写入 Redis 失败", e);
        }
        return token;
    }

    public LoginUser verify(String token) {
        Map<String, Object> payload = JwtUtil.verify(token);
        if (payload == null) {
            return null;
        }
        Object uIdObj = payload.get("uId");
        if (!(uIdObj instanceof Number)) {
            return null;
        }
        Integer uId = ((Number) uIdObj).intValue();
        String key = AuthConstant.buildTokenKey(uId, token);
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, Object> data;
        try {
            data = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
        Object idVal = data.get("uId");
        Integer storedUId = (idVal instanceof Number) ? ((Number) idVal).intValue() : null;
        if (storedUId == null) {
            return null;
        }
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0 && ttl < AuthConstant.RENEW_THRESHOLD_SECONDS) {
            stringRedisTemplate.expire(key, AuthConstant.DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
        }
        Object typeVal = data.get("uType");
        Integer uType = (typeVal instanceof Number) ? ((Number) typeVal).intValue() : null;
        return LoginUser.of(storedUId,
                data.get("uName") == null ? null : String.valueOf(data.get("uName")),
                data.get("realName") == null ? null : String.valueOf(data.get("realName")),
                uType);
    }

    public boolean revoke(String token) {
        Map<String, Object> payload = JwtUtil.verify(token);
        if (payload == null) {
            return false;
        }
        Object uIdObj = payload.get("uId");
        if (!(uIdObj instanceof Number)) {
            return false;
        }
        Integer uId = ((Number) uIdObj).intValue();
        String key = AuthConstant.buildTokenKey(uId, token);
        Boolean deleted = stringRedisTemplate.delete(key);
        return Boolean.TRUE.equals(deleted);
    }

    public long revokeAll(Integer uId) {
        String pattern = AuthConstant.LOGIN_TOKEN_KEY_PREFIX + uId + ":*";
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        return stringRedisTemplate.delete(keys);
    }
}
