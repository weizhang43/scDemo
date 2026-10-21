package com.example.scorder.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import response.ResponseDto;

/**
 * 站内消息服务间客户端（sc-user /user/message/inner/push）。
 * 推送为 best-effort：失败只记日志，不影响订单/支付主流程。
 */
@Component
@FeignClient(value = "sc-user", contextId = "userMsgClient", path = "/sc-user")
public interface UserMsgClient {

    /**
     * 落库一条站内消息并尝试 WebSocket 在线送达。
     */
    @PostMapping("/user/message/inner/push")
    ResponseDto<Object> push(@RequestParam("uId") Integer uId,
                             @RequestParam("msgType") String msgType,
                             @RequestParam("title") String title,
                             @RequestParam(value = "content", required = false) String content,
                             @RequestParam(value = "bizId", required = false) String bizId);
}
