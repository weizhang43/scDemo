package com.example.scuser.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 站内消息 WebSocket 端点。
 * 浏览器 WebSocket 无法携带 Authorization 头，因此连接地址走网关白名单放行，
 * 由本端点在握手时校验 query 中的 uId + token（与 HTTP 会话同一 Redis 体系）。
 * 服务完整地址 = context-path(/sc-user) + /user/ws。
 */
@Component
@ServerEndpoint("/user/ws")
public class MessageWebSocketServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageWebSocketServer.class);

    /** 客户端心跳报文 */
    private static final String PING = "ping";

    /** 单连接只做一次鉴权（onOpen 后重复消息不重复鉴权） */
    private final AtomicBoolean authenticated = new AtomicBoolean(false);

    private Integer uId;

    @OnOpen
    public void onOpen(Session session) throws IOException {
        Integer uid = intParam(session, "uId");
        String token = queryParam(session, "token");
        if (!SessionManager.getInstance().authenticate(uid, token)) {
            LOGGER.warn("[WS] 握手鉴权失败，拒绝连接 uId={}", uid);
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized"));
            return;
        }
        this.uId = uid;
        authenticated.set(true);
        SessionManager.getInstance().register(uid, session);
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        if (PING.equalsIgnoreCase(message) && authenticated.get()) {
            try {
                synchronized (session) {
                    session.getBasicRemote().sendText("pong");
                }
            } catch (IOException e) {
                LOGGER.debug("[WS] 心跳应答失败 uId={}", uId, e);
            }
        }
    }

    @OnClose
    public void onClose(Session session) {
        if (authenticated.get() && uId != null) {
            SessionManager.getInstance().unregister(uId, session);
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        if (authenticated.get() && uId != null) {
            SessionManager.getInstance().unregister(uId, session);
        }
        LOGGER.warn("[WS] 连接异常 uId={}", uId, error);
    }

    private static String queryParam(Session session, String name) {
        String value = session.getRequestParameterMap().get(name) == null
                ? null : session.getRequestParameterMap().get(name).get(0);
        return value == null ? null : value.trim();
    }

    private static Integer intParam(Session session, String name) {
        String raw = queryParam(session, name);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
