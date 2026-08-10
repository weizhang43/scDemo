package com.example.scgateway.filter;

import com.curry.model.auth.AuthConstant;
import com.curry.model.auth.JwtUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 全局鉴权过滤器。
 * 关键设计：用 ReactiveStringRedisTemplate 直接读 Redis 原始字符串，
 * 再自行 Jackson 反序列化为 Map。这样两端序列化器配置完全解耦，
 * 避免对象类型跨服务反序列化失败。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthGlobalFilter.class);

    /** 会话/JWT 载荷中用户 ID 的键名 */
    private static final String KEY_U_ID = "uId";
    /** HTTP GET 方法名，图片放行仅针对 GET 请求 */
    private static final String METHOD_GET = "GET";
    /** 商品图片查看路径前缀 */
    private static final String PRODUCT_IMAGE_PREFIX = "/product/image/";
    /** 通知图片查看路径前缀 */
    private static final String USER_IMAGE_PREFIX = "/user/image/";
    /** 日志中字符串的最大展示长度，超出部分截断 */
    private static final int ABBREVIATE_MAX_LEN = 80;
    /** 过滤器执行顺序值，越小优先级越高 */
    private static final int AUTH_FILTER_ORDER = -100;

    @Autowired
    private ReactiveStringRedisTemplate stringRedisTemplate;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Bean 初始化后打印一条日志，用于确认过滤器已被 Spring 加载及其执行顺序。
     */
    @PostConstruct
    public void init() {
        LOGGER.warn("[AuthFilter] AuthGlobalFilter 已加载, order={}", getOrder());
    }

    private static final String[] WHITELIST = {
            "/user/login",
            "/user/register",
            "/user/sendSmsCode",
            "/user/sendEmailCode",
            "/user/resetPassword",
            "/user/chat",
            "/user/parseImage",
            "/user/generateImage",
            "/actuator/**",
            // 模拟支付网关：收银台前端裸访问 + sc-order 商户调用（自带 HMAC 验签）
            "/pay/**",
            // 网关异步回调入口（sc-pay → sc-order），HMAC 验签 + nonce 防重放
            "/order/pay/notify",
            // 游客可见的只读接口：登录页公告 + 未登录浏览商品（均只有 GET 映射）
            "/user/notice/list",
            // 个人工作页（日报/周报）：登录页入口，免登录访问
            "/user/workReport/**",
            "/product/pageQuery",
            "/product/category/tree"
    };

    /**
     * 全局鉴权入口。执行流程：
     * 1. 命中白名单或图片直连放行规则的请求直接放行；
     * 2. 其余请求进入 token/会话校验链路，任一环节失败均返回 401。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod() == null ? METHOD_GET : request.getMethod().name();
        // 白名单与图片直连请求无需鉴权，直接进入后续过滤器链
        if (isWhitelisted(path) || isImagePassThrough(path, method)) {
            return chain.filter(exchange);
        }
        return authenticate(exchange, chain, path);
    }

    /**
     * 判断请求路径是否命中免鉴权白名单（Ant 风格匹配）。
     */
    private boolean isWhitelisted(String path) {
        for (String pattern : WHITELIST) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 图片查看直连放行判断（&lt;img src&gt; 无法携带 token）：
     * 对 /product/image/{fileName} 与 /user/image/{fileName} 且扩展名为图片的 GET 请求放行。
     */
    private boolean isImagePassThrough(String path, String method) {
        if (path.startsWith(PRODUCT_IMAGE_PREFIX)) {
            String fileName = path.substring(PRODUCT_IMAGE_PREFIX.length());
            boolean isImage = isImageFileName(fileName);
            LOGGER.info("[AuthFilter] 图片路径检查 method={}, fileName={}, isImage={}", method, fileName, isImage);
            return METHOD_GET.equalsIgnoreCase(method) && isImage;
        }
        if (path.startsWith(USER_IMAGE_PREFIX)) {
            // 通知图片：同样仅放行图片扩展名的 GET 请求
            String fileName = path.substring(USER_IMAGE_PREFIX.length());
            return METHOD_GET.equalsIgnoreCase(method) && isImageFileName(fileName);
        }
        return false;
    }

    /**
     * 判断文件名是否为不含子路径的图片文件（按扩展名白名单匹配）。
     */
    private static boolean isImageFileName(String fileName) {
        return fileName.indexOf('/') < 0
                && fileName.toLowerCase().matches(".*\\.(png|jpg|jpeg|gif|webp)$");
    }

    /**
     * 鉴权入口：校验请求头中的 Bearer token 格式，格式合法则继续校验 JWT 签名。
     */
    private Mono<Void> authenticate(ServerWebExchange exchange, GatewayFilterChain chain, String path) {
        String auth = exchange.getRequest().getHeaders().getFirst(AuthConstant.HEADER_AUTHORIZATION);
        if (auth == null || !auth.startsWith(AuthConstant.BEARER_PREFIX)) {
            return unauthorized(exchange, "未登录或 token 缺失");
        }
        String token = auth.substring(AuthConstant.BEARER_PREFIX.length()).trim();
        return verifyToken(exchange, chain, token, path);
    }

    /**
     * 校验 JWT 签名与载荷合法性：从载荷中取出 uId，拼出 Redis key 后进入登录态校验。
     */
    private Mono<Void> verifyToken(ServerWebExchange exchange, GatewayFilterChain chain,
                                   String token, String path) {
        Map<String, Object> payload = JwtUtil.verify(token);
        if (payload == null) {
            return unauthorized(exchange, "token 无效或已篡改");
        }
        Object uIdObj = payload.get(KEY_U_ID);
        if (!(uIdObj instanceof Number)) {
            return unauthorized(exchange, "token 内容非法");
        }
        Integer uId = ((Number) uIdObj).intValue();
        String redisKey = AuthConstant.buildTokenKey(uId, token);
        LOGGER.info("[AuthFilter] 校验 token, redisKey={}, path={}", redisKey, path);
        return verifySession(exchange, chain, redisKey);
    }

    /**
     * 从 Redis 校验登录态：key 存在则处理会话内容，不存在视为登录已过期。
     */
    private Mono<Void> verifySession(ServerWebExchange exchange, GatewayFilterChain chain, String redisKey) {
        return stringRedisTemplate.opsForValue().get(redisKey)
                .flatMap(raw -> handleSession(exchange, chain, redisKey, raw))
                .switchIfEmpty(Mono.defer(() -> {
                    LOGGER.warn("[AuthFilter] 登录态失效: Redis 中未找到 key={}", redisKey);
                    return unauthorizedMono(exchange, "登录已过期，请重新登录");
                }));
    }

    /**
     * 处理 Redis 中命中的会话 JSON：解析失败或缺 uId 视为登录态异常；
     * 缺 uType 的存量会话直接销毁强制重登；校验通过后放行并透传用户信息。
     */
    private Mono<Void> handleSession(ServerWebExchange exchange, GatewayFilterChain chain,
                                     String redisKey, String raw) {
        LOGGER.info("[AuthFilter] 命中 Redis, key={}, raw={}", redisKey, abbreviate(raw));
        Map<String, Object> data = parseMap(raw);
        if (data == null || data.get(KEY_U_ID) == null) {
            LOGGER.warn("[AuthFilter] 登录态失效: Redis raw 异常或缺失 uId, key={}, raw={}",
                    redisKey, abbreviate(raw));
            return unauthorizedMono(exchange, "登录状态异常，请重新登录");
        }
        // 本次改动前签发的会话 JSON 里没有 uType，无法判定角色。
        // 不做「缺失即当管理员」兜底——那等于给存量顾客会话开一个 TTL 时长的跨商家权限。
        // 直接销毁会话强制重登一次，语义无歧义。
        Object typeVal = data.get("uType");
        if (!(typeVal instanceof Number)) {
            LOGGER.warn("[AuthFilter] 会话缺少 uType（改动前签发），销毁并要求重新登录, key={}", redisKey);
            return stringRedisTemplate.delete(redisKey)
                    .then(unauthorizedMono(exchange, "登录信息需要更新，请重新登录"));
        }
        return relayWithUserHeaders(exchange, chain, redisKey, data);
    }

    /**
     * 校验通过后的放行动作：把用户信息写入下游请求头，续期 Redis 登录态后继续过滤器链。
     */
    private Mono<Void> relayWithUserHeaders(ServerWebExchange exchange, GatewayFilterChain chain,
                                            String redisKey, Map<String, Object> data) {
        String uIdStr = String.valueOf(data.get(KEY_U_ID));
        String uName = data.get("uName") == null ? "" : String.valueOf(data.get("uName"));
        String realName = data.get("realName") == null ? "" : String.valueOf(data.get("realName"));
        String uType = String.valueOf(((Number) data.get("uType")).intValue());

        // 将用户身份透传给下游服务，业务模块从请求头直接取用
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(AuthConstant.HEADER_X_USER_ID, uIdStr)
                .header(AuthConstant.HEADER_X_USER_NAME, uName)
                .header(AuthConstant.HEADER_X_REAL_NAME, realName)
                .header(AuthConstant.HEADER_X_USER_TYPE, uType)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutated).build();
        return stringRedisTemplate.expire(redisKey,
                        Duration.ofSeconds(AuthConstant.DEFAULT_TTL_SECONDS))
                .then(chain.filter(mutatedExchange));
    }

    /**
     * 将 Redis 中的原始 JSON 字符串反序列化为 Map。
     * 入参为空或解析失败时返回 null，由调用方按"登录态失效"处理。
     */
    private Map<String, Object> parseMap(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // 解析失败返回 null，由调用方按"登录态失效"处理，raw 仅写入日志用于排查
            LOGGER.warn("[AuthFilter] Redis 会话 JSON 解析失败, raw={}", abbreviate(raw), e);
            return null;
        }
    }

    /**
     * 截断过长字符串（超过 {@link #ABBREVIATE_MAX_LEN} 字符加省略号），用于日志与错误消息，避免刷屏。
     */
    private static String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= ABBREVIATE_MAX_LEN ? s : s.substring(0, ABBREVIATE_MAX_LEN) + "...";
    }

    /**
     * 响应式链路中（Redis 校验阶段）返回 401 的封装。
     */
    private Mono<Void> unauthorizedMono(ServerWebExchange exchange, String msg) {
        return writeUnauthorized(exchange, msg);
    }

    /**
     * 进入响应式链路之前（token 格式/签名校验阶段）返回 401 的封装。
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        return writeUnauthorized(exchange, msg);
    }

    /**
     * 实际写出 401 响应：设置状态码与 JSON 内容类型，输出统一的 {code,msg} 结构。
     * 响应已提交（如异常处理链路已写出部分内容）时不再改 header，仅记日志，避免 ReadOnlyHttpHeaders 异常。
     */
    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            LOGGER.warn("[AuthFilter] 响应已提交，跳过 401 写出: {}", msg);
            return Mono.empty();
        }
        try {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        } catch (Exception e) {
            LOGGER.warn("[AuthFilter] 设置响应头失败，仍尝试写出 body", e);
        }
        String body = "{\"code\":401,\"msg\":\"" + msg.replace("\"", "'") + "\"}";
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 过滤器执行顺序，值越小优先级越高，确保鉴权早于其他业务过滤器执行。
     */
    @Override
    public int getOrder() {
        return AUTH_FILTER_ORDER;
    }
}
