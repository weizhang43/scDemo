package com.example.scuser.controller;

import com.curry.model.Message;
import com.curry.model.auth.AuthConstant;
import com.example.scuser.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

/**
 * 站内消息接口。
 * /inner/push 为服务间内部接口（sc-order 经 Feign 调用）：需携带内部令牌或用户上下文。
 */
@RestController
@RequestMapping("/user/message")
public class MessageController {

    @Autowired
    private MessageService messageService;

    /** 服务间内部调用令牌，与网关侧/调用方保持一致 */
    @Value("${inner.auth.token:}")
    private String innerToken;

    /**
     * 分页查询我的消息。
     */
    @GetMapping("/page")
    public ResponseDto<Message> page(@RequestParam(value = "isRead", required = false) Integer isRead,
                                     @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                     @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                     @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return messageService.pageQuery(uId, isRead, pageNum, pageSize);
    }

    /**
     * 我的未读消息数。
     */
    @GetMapping("/unread-count")
    public ResponseDto<Message> unreadCount(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return messageService.unreadCount(uId);
    }

    /**
     * 标记单条消息已读。
     */
    @PostMapping("/{id}/read")
    public ResponseDto<Message> markRead(@PathVariable("id") Long id,
                                         @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return messageService.markRead(uId, id);
    }

    /**
     * 全部标记已读。
     */
    @PostMapping("/read-all")
    public ResponseDto<Message> markAllRead(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return messageService.markAllRead(uId);
    }

    /**
     * 服务间推送入口：落库并尝试在线送达。鉴权要求内部令牌或真实用户上下文二选一。
     */
    @PostMapping("/inner/push")
    public ResponseDto<Message> innerPush(@RequestParam("uId") Integer uId,
                                          @RequestParam("msgType") String msgType,
                                          @RequestParam("title") String title,
                                          @RequestParam(value = "content", required = false) String content,
                                          @RequestParam(value = "bizId", required = false) String bizId,
                                          @RequestHeader(value = AuthConstant.HEADER_X_INNER_TOKEN, required = false) String inner,
                                          @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer callerUId) {
        if (!isInternalCall(inner, callerUId)) {
            return ResponseDto.error("无权限");
        }
        Message message = messageService.push(uId, msgType, title, content, bizId);
        return message == null ? ResponseDto.error("推送失败") : ResponseDto.success(message);
    }

    /**
     * 内部调用判定：携带匹配的内部令牌，或请求本身带用户上下文（Feign 拦截器透传）。
     */
    private boolean isInternalCall(String inner, Integer callerUId) {
        boolean innerOk = innerToken != null && !innerToken.isEmpty() && innerToken.equals(inner);
        return innerOk || callerUId != null;
    }
}
