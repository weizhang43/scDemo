package com.example.scorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.curry.model.OrderItem;
import com.curry.model.Product;
import com.example.scorder.entity.OrderStockRestoreMsg;
import com.example.scorder.mapper.OrderItemMapper;
import com.example.scorder.mapper.OrderStockRestoreMsgMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import response.ResponseDto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static response.ResponseDto.SUCCESS_CODE;

/**
 * 取消订单回库存本地消息表消费者。
 * 定时扫描 t_order_stock_restore_msg 中 status=0 且 next_retry<=now 的消息，
 * 调用 sc-product addStock 回库存，成功后置 status=1 并删除对应订单明细，
 * 失败按指数退避重试，超过 max_retry 置 status=2 等待人工介入。
 */
@Service
@Slf4j
public class StockRestoreMsgConsumer {

    @Autowired
    private OrderStockRestoreMsgMapper orderStockRestoreMsgMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private com.example.scorder.mapper.OrderMapper orderMapper;

    @Autowired
    private com.example.scorder.client.CouponClient couponClient;

    @Autowired
    private OrderFeignService orderFeignService;

    private static final int BATCH_SIZE = 50;

    /**
     * 每 5s 扫一次待处理消息。
     */
    @Scheduled(fixedDelay = 5000)
    public void scan() {
        Date now = new Date();
        LambdaQueryWrapper<OrderStockRestoreMsg> qw = new LambdaQueryWrapper<>();
        qw.eq(OrderStockRestoreMsg::getStatus, 0)
          .le(OrderStockRestoreMsg::getNextRetry, now)
          .orderByAsc(OrderStockRestoreMsg::getId)
          .last("LIMIT " + BATCH_SIZE);
        List<OrderStockRestoreMsg> list = orderStockRestoreMsgMapper.selectList(qw);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (OrderStockRestoreMsg msg : list) {
            try {
                processOne(msg);
            } catch (Exception ex) {
                log.error("处理回库存消息异常 msgId={} oId={}", msg.getId(), msg.getOId(), ex);
            }
        }
    }

    /**
     * 真正消费单条消息，单独方法以便 @Transactional 生效。
     * 整个流程：远程加库存 -> 成功后删除 t_order_item + 标记 msg status=1。
     */
    @Transactional(rollbackFor = Exception.class)
    public void processOne(OrderStockRestoreMsg msg) {
        // 先返还优惠券再回库存：券返还幂等(1|2→0)可自由重试，
        // addStock 不幂等 —— 若放在后面失败重试会重复加库存
        if (!restoreCouponIfAny(msg)) {
            markRetryOrFail(msg);
            return;
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOId, msg.getOId()));
        if (items == null || items.isEmpty()) {
            // 无明细可恢复，直接置完成
            markDone(msg);
            return;
        }
        List<Product> productList = new ArrayList<>(items.size());
        for (OrderItem it : items) {
            productList.add(new Product(it.getPId(), it.getQuantity()));
        }
        ResponseDto<Product> resp;
        try {
            resp = orderFeignService.addStock(productList);
        } catch (Exception ex) {
            log.warn("调 addStock 异常 msgId={} oId={}，触发重试", msg.getId(), msg.getOId(), ex);
            markRetryOrFail(msg);
            return;
        }
        if (resp != null && SUCCESS_CODE.equals(resp.getCode())) {
            // 成功：置 msg status=1；仅取消来源(source=0)删除订单明细 ——
            // 售后退款(source=1)的订单仍存续，明细要留着供详情页与评价展示
            if (msg.getSource() == null || msg.getSource() == 0) {
                orderItemMapper.delete(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOId, msg.getOId()));
            }
            markDone(msg);
        } else {
            log.warn("addStock 业务失败 msgId={} oId={} msg={}", msg.getId(), msg.getOId(),
                    resp == null ? "null" : resp.getMsg());
            markRetryOrFail(msg);
        }
    }

    /**
     * 订单用了券则调 sc-product 返还（取消与售后退款两条链路共用）。
     * 返回 false 表示需要重试；无券或返还成功返回 true。
     */
    private boolean restoreCouponIfAny(OrderStockRestoreMsg msg) {
        com.curry.model.Order order = orderMapper.selectById(msg.getOId());
        if (order == null || order.getCouponId() == null) {
            return true;
        }
        try {
            ResponseDto<Object> resp = couponClient.restore(order.getCouponId());
            if (resp != null && SUCCESS_CODE.equals(resp.getCode())) {
                return true;
            }
            log.warn("券返还业务失败 msgId={} oId={} couponId={} msg={}", msg.getId(), msg.getOId(),
                    order.getCouponId(), resp == null ? "null" : resp.getMsg());
        } catch (Exception ex) {
            log.warn("调券返还异常 msgId={} oId={} couponId={}，触发重试",
                    msg.getId(), msg.getOId(), order.getCouponId(), ex);
        }
        return false;
    }

    private void markDone(OrderStockRestoreMsg msg) {
        OrderStockRestoreMsg update = new OrderStockRestoreMsg();
        update.setId(msg.getId());
        update.setStatus(1);
        orderStockRestoreMsgMapper.updateById(update);
    }

    /**
     * 重试计数 +1，超过 max_retry 置 status=2；否则按指数退避更新 next_retry。
     * 注意：用 status=0 作为 CAS 条件避免和并发消费冲突。
     */
    private void markRetryOrFail(OrderStockRestoreMsg msg) {
        int newRetry = msg.getRetryCnt() + 1;
        OrderStockRestoreMsg update = new OrderStockRestoreMsg();
        update.setId(msg.getId());
        if (newRetry >= msg.getMaxRetry()) {
            update.setStatus(2);
        } else {
            update.setRetryCnt(newRetry);
            long backoffMs = (1L << newRetry) * 10_000L; // 20s, 40s, 80s ...
            update.setNextRetry(new Date(System.currentTimeMillis() + backoffMs));
        }
        // CAS 防止并发消费同一消息时覆盖
        int affected = orderStockRestoreMsgMapper.update(update,
                new LambdaQueryWrapper<OrderStockRestoreMsg>()
                        .eq(OrderStockRestoreMsg::getId, msg.getId())
                        .eq(OrderStockRestoreMsg::getStatus, 0));
        if (affected == 0) {
            log.info("消息 {} 已被其他线程处理，跳过", msg.getId());
        }
    }

    /**
     * 暴露给管理端手动重置失败消息。
     */
    public void resetFailed(Long id) {
        OrderStockRestoreMsg update = new OrderStockRestoreMsg();
        update.setId(id);
        update.setStatus(0);
        update.setRetryCnt(0);
        update.setNextRetry(new Date());
        orderStockRestoreMsgMapper.update(update,
                new LambdaQueryWrapper<OrderStockRestoreMsg>()
                        .eq(OrderStockRestoreMsg::getId, id)
                        .eq(OrderStockRestoreMsg::getStatus, 2));
    }
}
