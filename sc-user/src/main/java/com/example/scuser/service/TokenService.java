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

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenService.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 签发 JWT 并把用户上下文以 JSON 形式写入 Redis（带 TTL）。
     * @param user 登录用户上下文
     * @return 签发的 token
     */
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
            LOGGER.info("[TokenService] 写入 Redis 成功 key={} json={}", key, json);
        } catch (Exception e) {
            LOGGER.error("[TokenService] 写入 Redis 失败 key={}", key, e);
            throw new IllegalStateException("写入 Redis 失败", e);
        }
        return token;
    }

    /**
     * 校验 token：签名有效且 Redis 中存在对应会话则返回登录用户，否则返回 null；
     * 剩余有效期低于阈值时顺带续期。
     * @param token 待校验的 JWT
     * @return 登录用户上下文，校验失败返回 null
     */
    public LoginUser verify(String token) {
        Integer uId = extractUid(JwtUtil.verify(token));
        if (uId == null) {
            return null;
        }
        String key = AuthConstant.buildTokenKey(uId, token);
        Map<String, Object> data = readTokenData(key);
        if (data == null) {
            return null;
        }
        return buildLoginUser(key, data);
    }

    /**
     * 从 JWT payload 中提取用户 ID，payload 无效时返回 null。
     */
    private Integer extractUid(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object uIdObj = payload.get("uId");
        return uIdObj instanceof Number ? ((Number) uIdObj).intValue() : null;
    }

    /**
     * 读取并解析 Redis 中的会话 JSON，缺失或解析失败返回 null。
     */
    private Map<String, Object> readTokenData(String key) {
        String raw = stringRedisTemplate.opsForValue().get(key);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            LOGGER.warn("[TokenService] 会话 JSON 解析失败 key={}", key, e);
            return null;
        }
    }

    /**
     * 由会话数据构建登录用户，必要时对快过期的会话续期。
     */
    private LoginUser buildLoginUser(String key, Map<String, Object> data) {
        Object idVal = data.get("uId");
        Integer storedUId = (idVal instanceof Number) ? ((Number) idVal).intValue() : null;
        if (storedUId == null) {
            return null;
        }
        renewIfNeeded(key);
        Object typeVal = data.get("uType");
        Integer uType = (typeVal instanceof Number) ? ((Number) typeVal).intValue() : null;
        return LoginUser.of(storedUId,
                data.get("uName") == null ? null : String.valueOf(data.get("uName")),
                data.get("realName") == null ? null : String.valueOf(data.get("realName")),
                uType);
    }

    /**
     * 剩余有效期低于续期阈值时，重置为默认 TTL。
     */
    private void renewIfNeeded(String key) {
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0 && ttl < AuthConstant.RENEW_THRESHOLD_SECONDS) {
            stringRedisTemplate.expire(key, AuthConstant.DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 注销单个 token 对应的会话。
     * @param token 待注销的 JWT
     * @return 是否删除了会话
     */
    public boolean revoke(String token) {
        Integer uId = extractUid(JwtUtil.verify(token));
        if (uId == null) {
            return false;
        }
        Boolean deleted = stringRedisTemplate.delete(AuthConstant.buildTokenKey(uId, token));
        return Boolean.TRUE.equals(deleted);
    }

    /**
     * 注销指定用户的全部会话（如改密后强制下线）。
     * @param uId 用户 ID
     * @return 删除的会话数量
     */
    public long revokeAll(Integer uId) {
        String pattern = AuthConstant.LOGIN_TOKEN_KEY_PREFIX + uId + ":*";
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        return stringRedisTemplate.delete(keys);
    }
}
