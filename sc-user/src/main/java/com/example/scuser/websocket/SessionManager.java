package com.example.scuser.websocket;

import com.example.scuser.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.websocket.Session;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理：uId → 在线连接。
 * @ServerEndpoint 实例由容器反射创建、不受 Spring 管理，故用静态实例持有者
 * 把 Spring Bean（TokenService 等）暴露给端点使用。
 */
@Component
public class SessionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);

    private static SessionManager instance;

    @Autowired
    private TokenService tokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 在线用户会话表：同一用户多标签页登录时只保留最新连接（旧连接被顶替后无法再收消息，由前端重连恢复） */
    private final ConcurrentHashMap<Integer, Session> sessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        instance = this;
    }

    public static SessionManager getInstance() {
        return instance;
    }

    /**
     * 握手鉴权：校验 uId + token 对应的 Redis 会话有效。
     * @return 鉴权是否通过
     */
    public boolean authenticate(Integer uId, String token) {
        if (uId == null || token == null || token.isEmpty()) {
            return false;
        }
        try {
            return tokenService.verify(token) != null;
        } catch (Exception e) {
            LOGGER.warn("[WS] 会话校验异常 uId={}", uId, e);
            return false;
        }
    }

    /**
     * 登记在线连接（顶替同 uId 旧连接）。
     */
    public void register(Integer uId, Session session) {
        Session old = sessions.put(uId, session);
        if (old != null && old.isOpen() && old != session) {
            try {
                old.close();
            } catch (IOException e) {
                LOGGER.debug("[WS] 关闭被顶替连接失败 uId={}", uId, e);
            }
        }
        LOGGER.info("[WS] 用户上线 uId={}, 当前在线={}", uId, sessions.size());
    }

    /**
     * 连接关闭时移除登记（仅当登记的还是本连接时移除，避免误删顶替后的新连接）。
     */
    public void unregister(Integer uId, Session session) {
        sessions.remove(uId, session);
        LOGGER.info("[WS] 用户下线 uId={}, 当前在线={}", uId, sessions.size());
    }

    /**
     * 向指定用户推送一条消息 JSON，离线静默跳过。
     */
    public void push(Integer uId, Object payload) {
        Session session = sessions.get(uId);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                session.getBasicRemote().sendText(json);
            }
        } catch (Exception e) {
            LOGGER.warn("[WS] 推送失败 uId={}", uId, e);
        }
    }
}
