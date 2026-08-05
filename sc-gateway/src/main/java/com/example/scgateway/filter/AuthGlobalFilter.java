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

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);

    @Autowired
    private ReactiveStringRedisTemplate stringRedisTemplate;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Bean 初始化后打印一条日志，用于确认过滤器已被 Spring 加载及其执行顺序。
     */
    @PostConstruct
    public void init() {
        log.warn("[AuthFilter] AuthGlobalFilter 已加载, order={}", getOrder());
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
            "/product/pageQuery",
            "/product/category/tree"
    };

    /**
     * 全局鉴权入口。执行流程：
     * 1. 命中白名单的请求直接放行；
     * 2. 校验请求头中的 Bearer token 格式与 JWT 签名；
     * 3. 从 JWT 载荷中取出 uId，拼出 Redis key 校验登录态是否仍有效；
     * 4. 校验通过后把用户信息写入下游请求头，并续期 Redis key，再放行；
     * 5. 任一环节失败均返回 401。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        for (String pattern : WHITELIST) {
            if (pathMatcher.match(pattern, path)) {
                return chain.filter(exchange);
            }
        }

        // 图片查看（<img src> 无法携带 token）：对 /product/image/{fileName} 且扩展名为图片的 GET 请求放行
        String method = request.getMethod() == null ? "GET" : request.getMethod().name();
        if (path.startsWith("/product/image/")) {
            String fileName = path.substring("/product/image/".length());
            boolean isImage = fileName.indexOf('/') < 0
                    && fileName.toLowerCase().matches(".*\\.(png|jpg|jpeg|gif|webp)$");
            log.info("[AuthFilter] 图片路径检查 method={}, fileName={}, isImage={}", method, fileName, isImage);

            if ("GET".equalsIgnoreCase(method) && isImage) {
                return chain.filter(exchange);
            }
        }

        // 通知图片查看：对 /user/image/{fileName} 且扩展名为图片的 GET 请求放行
        if (path.startsWith("/user/image/")) {
            String fileName = path.substring("/user/image/".length());
            boolean isImage = fileName.indexOf('/') < 0
                    && fileName.toLowerCase().matches(".*\\.(png|jpg|jpeg|gif|webp)$");
            if ("GET".equalsIgnoreCase(method) && isImage) {
                return chain.filter(exchange);
            }
        }

        String auth = request.getHeaders().getFirst(AuthConstant.HEADER_AUTHORIZATION);
        if (auth == null || !auth.startsWith(AuthConstant.BEARER_PREFIX)) {
            return unauthorized(exchange, "未登录或 token 缺失");
        }
        String token = auth.substring(AuthConstant.BEARER_PREFIX.length()).trim();
        Map<String, Object> payload = JwtUtil.verify(token);
        if (payload == null) {
            return unauthorized(exchange, "token 无效或已篡改");
        }
        Object uIdObj = payload.get("uId");
        if (!(uIdObj instanceof Number)) {
            return unauthorized(exchange, "token 内容非法");
        }
        Integer uId = ((Number) uIdObj).intValue();
        String redisKey = AuthConstant.buildTokenKey(uId, token);
        log.info("[AuthFilter] 校验 token, redisKey={}, path={}", redisKey, path);

        return stringRedisTemplate.opsForValue().get(redisKey)
                .flatMap(raw -> {
                    log.info("[AuthFilter] 命中 Redis, key={}, raw={}", redisKey, abbreviate(raw));
                    Map<String, Object> data = parseMap(raw);
                    if (data == null || data.get("uId") == null) {
                        log.warn("[AuthFilter] 登录态失效: Redis raw 异常或缺失 uId, key={}, raw={}", redisKey, abbreviate(raw));
                        return unauthorizedMono(exchange, "登录状态异常，请重新登录");
                    }
                    String uIdStr = String.valueOf(data.get("uId"));
                    String uName = data.get("uName") == null ? "" : String.valueOf(data.get("uName"));
                    String realName = data.get("realName") == null ? "" : String.valueOf(data.get("realName"));

                    // 本次改动前签发的会话 JSON 里没有 uType，无法判定角色。
                    // 不做「缺失即当管理员」兜底——那等于给存量顾客会话开一个 TTL 时长的跨商家权限。
                    // 直接销毁会话强制重登一次，语义无歧义。
                    Object typeVal = data.get("uType");
                    if (!(typeVal instanceof Number)) {
                        log.warn("[AuthFilter] 会话缺少 uType（改动前签发），销毁并要求重新登录, key={}", redisKey);
                        return stringRedisTemplate.delete(redisKey)
                                .then(unauthorizedMono(exchange, "登录信息需要更新，请重新登录"));
                    }
                    String uType = String.valueOf(((Number) typeVal).intValue());

                    ServerHttpRequest mutated = request.mutate()
                            .header(AuthConstant.HEADER_X_USER_ID, uIdStr)
                            .header(AuthConstant.HEADER_X_USER_NAME, uName)
                            .header(AuthConstant.HEADER_X_REAL_NAME, realName)
                            .header(AuthConstant.HEADER_X_USER_TYPE, uType)
                            .build();
                    ServerWebExchange mutatedExchange = exchange.mutate().request(mutated).build();
                    return stringRedisTemplate.expire(redisKey,
                                    Duration.ofSeconds(AuthConstant.DEFAULT_TTL_SECONDS))
                            .then(chain.filter(mutatedExchange));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("[AuthFilter] 登录态失效: Redis 中未找到 key={}", redisKey);
                    return unauthorizedMono(exchange, "登录已过期，请重新登录");
                }));
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
            return null;
        }
    }

    /**
     * 截断过长字符串（超过 80 字符加省略号），用于日志与错误消息，避免刷屏。
     */
    private static String abbreviate(String s) {
        if (s == null) return "null";
        return s.length() <= 80 ? s : s.substring(0, 80) + "...";
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
            log.warn("[AuthFilter] 响应已提交，跳过 401 写出: {}", msg);
            return Mono.empty();
        }
        try {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        } catch (Exception e) {
            log.warn("[AuthFilter] 设置响应头失败，仍尝试写出 body: {}", e.getMessage());
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
        return -100;
    }
}
