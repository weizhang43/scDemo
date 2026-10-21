package com.example.scorder.service.impl;

import com.example.scorder.client.UserMsgClient;
import com.example.scorder.mapper.OrderItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import response.ResponseDto;

import java.util.List;

/**
 * 站内消息通知辅助：统一封装「事务提交后 best-effort 推送」。
 * 事务内先注册、提交后再外呼，避免回滚后产生幽灵消息；推送失败仅记日志。
 */
@Component
public class MessageNotifyHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageNotifyHelper.class);

    /** 消息类型：订单 */
    public static final String TYPE_ORDER = "order";

    /** 消息类型：售后 */
    public static final String TYPE_AFTERSALE = "aftersale";

    @Autowired
    private UserMsgClient userMsgClient;

    @Autowired
    private OrderItemMapper orderItemMapper;

    /**
     * 注册事务提交后向指定用户推送（须在事务内调用）。
     */
    public void pushAfterCommit(final Integer uId, final String msgType, final String title,
                                final String content, final String bizId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                pushQuietly(uId, msgType, title, content, bizId);
            }
        });
    }

    /**
     * 注册事务提交后通知订单关联的各商家（按订单明细 DISTINCT merchant_id）。
     */
    public void notifyMerchantsAfterCommit(final Integer oId, final String title, final String content) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyMerchantsOfOrder(oId, title, content);
            }
        });
    }

    /**
     * 直接通知订单关联的各商家（无事务上下文或已在 afterCommit 内时使用）。
     */
    public void notifyMerchantsOfOrder(Integer oId, String title, String content) {
        if (oId == null) {
            return;
        }
        try {
            List<Integer> merchantUIds = orderItemMapper.selectMerchantUIds(oId);
            if (merchantUIds == null || merchantUIds.isEmpty()) {
                return;
            }
            for (Integer merchantUId : merchantUIds) {
                pushQuietly(merchantUId, TYPE_ORDER, title, content, String.valueOf(oId));
            }
        } catch (Exception e) {
            LOGGER.warn("[Notify] 商家通知失败 oId={}", oId, e);
        }
    }

    /**
     * 直接推送（无事务上下文时使用），失败静默。
     */
    public void pushQuietly(Integer uId, String msgType, String title, String content, String bizId) {
        if (uId == null) {
            return;
        }
        try {
            ResponseDto<Object> resp = userMsgClient.push(uId, msgType, title, content, bizId);
            if (resp == null || !Integer.valueOf(200).equals(resp.getCode())) {
                LOGGER.warn("[Notify] 站内消息推送未成功 uId={}, title={}, msg={}",
                        uId, title, resp == null ? null : resp.getMsg());
            }
        } catch (Exception e) {
            LOGGER.warn("[Notify] 站内消息推送失败 uId={}, title={}", uId, title, e);
        }
    }
}
