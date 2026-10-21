package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.Message;
import response.ResponseDto;

/**
 * 站内消息服务：收件人维度的分页查询、未读数、已读标记，以及落库+在线推送。
 */
public interface MessageService extends IService<Message> {

    /**
     * 分页查询当前用户的消息。
     * @param uId 收件人
     * @param isRead 已读过滤（null=全部）
     */
    ResponseDto<Message> pageQuery(Integer uId, Integer isRead, Integer pageNum, Integer pageSize);

    /**
     * 当前用户未读消息数。
     */
    ResponseDto<Message> unreadCount(Integer uId);

    /**
     * 标记单条消息已读（仅限本人的消息）。
     */
    ResponseDto<Message> markRead(Integer uId, Long id);

    /**
     * 当前用户消息全部置为已读。
     */
    ResponseDto<Message> markAllRead(Integer uId);

    /**
     * 落库一条消息并尝试实时推送给在线收件人。
     * @return 落库后的消息
     */
    Message push(Integer uId, String msgType, String title, String content, String bizId);
}
