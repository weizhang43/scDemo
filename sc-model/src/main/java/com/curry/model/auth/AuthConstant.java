package com.curry.model.auth;

/**
 * 鉴权相关常量与 Redis Key 规则
 */
public final class AuthConstant {

    private AuthConstant() {}

    /** 登录 token 在 Redis 中的 key 前缀，形如 login:token:{uId}:{token} */
    public static final String LOGIN_TOKEN_KEY_PREFIX = "login:token:";

    /** 登录 token 默认有效期（秒）：2 小时 */
    public static final long DEFAULT_TTL_SECONDS = 2 * 60 * 60L;

    /** 续期阈值（秒）：剩余有效期小于该值则续期，默认 30 分钟 */
    public static final long RENEW_THRESHOLD_SECONDS = 30 * 60L;

    /** 请求头：Authorization，值为 "Bearer {token}" */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** 请求头：Gateway 透传到下游的用户 ID */
    public static final String HEADER_X_USER_ID = "X-User-Id";

    /** 请求头：Gateway 透传到下游的用户名 */
    public static final String HEADER_X_USER_NAME = "X-User-Name";

    /** 请求头：Gateway 透传到下游的真实姓名 */
    public static final String HEADER_X_REAL_NAME = "X-Real-Name";

    /** Bearer 前缀 */
    public static final String BEARER_PREFIX = "Bearer ";

    public static String buildTokenKey(Integer uId, String token) {
        return LOGIN_TOKEN_KEY_PREFIX + uId + ":" + token;
    }
}
