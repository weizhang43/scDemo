package com.example.scuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Message;
import com.example.scuser.mapper.MessageMapper;
import com.example.scuser.service.MessageService;
import com.example.scuser.websocket.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import java.util.Date;

/**
 * 站内消息服务实现。推送链路：先落库保证离线可补看，再尝试 WebSocket 在线送达。
 */
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageServiceImpl.class);

    private static final int DEFAULT_PAGE_SIZE = 10;

    private static final int MSG_TYPE_MAX_LENGTH = 32;

    private static final int TITLE_MAX_LENGTH = 200;

    private static final int CONTENT_MAX_LENGTH = 1000;

    private static final int BIZ_ID_MAX_LENGTH = 64;

    @Override
    public ResponseDto<Message> pageQuery(Integer uId, Integer isRead, Integer pageNum, Integer pageSize) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        Page<Message> page = new Page<>(current, size);
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getUId, uId)
                .eq(isRead != null, Message::getIsRead, isRead)
                .orderByDesc(Message::getId);
        page = baseMapper.selectPage(page, wrapper);
        return ResponseDto.success(page);
    }

    @Override
    public ResponseDto<Message> unreadCount(Integer uId) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUId, uId)
                .eq(Message::getIsRead, 0));
        return ResponseDto.success(count == null ? 0L : count);
    }

    @Override
    public ResponseDto<Message> markRead(Integer uId, Long id) {
        if (id == null) {
            return ResponseDto.error("消息ID不能为空");
        }
        baseMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getId, id)
                .eq(Message::getUId, uId)
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1));
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<Message> markAllRead(Integer uId) {
        baseMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getUId, uId)
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1));
        return ResponseDto.success(null);
    }

    @Override
    public Message push(Integer uId, String msgType, String title, String content, String bizId) {
        if (uId == null) {
            LOGGER.warn("[Message] 推送缺少收件人，忽略 title={}", title);
            return null;
        }
        Message message = new Message();
        message.setUId(uId);
        message.setMsgType(abbreviate(msgType, MSG_TYPE_MAX_LENGTH, "system"));
        message.setTitle(abbreviate(title, TITLE_MAX_LENGTH, "新消息"));
        message.setContent(abbreviate(content, CONTENT_MAX_LENGTH, null));
        message.setBizId(abbreviate(bizId, BIZ_ID_MAX_LENGTH, null));
        message.setIsRead(0);
        message.setCreateTime(new Date());
        baseMapper.insert(message);
        try {
            SessionManager.getInstance().push(uId, message);
        } catch (Exception e) {
            LOGGER.warn("[Message] 在线推送失败 uId={}, id={}", uId, message.getId(), e);
        }
        return message;
    }

    private static String abbreviate(String raw, int max, String fallback) {
        String value = raw == null ? null : raw.trim();
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
