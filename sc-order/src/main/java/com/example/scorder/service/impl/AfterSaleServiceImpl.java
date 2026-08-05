package com.example.scorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Order;
import com.example.scorder.auth.OrderScope;
import com.example.scorder.client.MockPayGatewayClient;
import com.example.scorder.dto.AfterSaleApplyRequest;
import com.example.scorder.entity.AfterSale;
import com.example.scorder.entity.OrderStockRestoreMsg;
import com.example.scorder.entity.PayRecord;
import com.example.scorder.mapper.AfterSaleMapper;
import com.example.scorder.mapper.OrderMapper;
import com.example.scorder.mapper.OrderStockRestoreMsgMapper;
import com.example.scorder.mapper.PayRecordMapper;
import com.example.scorder.service.AfterSaleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import response.ResponseDto;

import java.util.Date;
import java.util.List;

import static com.example.scorder.service.impl.OrderServiceImpl.COMPLETE_ORDER_STATUS;
import static com.example.scorder.service.impl.OrderServiceImpl.SHIPPED_ORDER_STATUS;

@Service
@Slf4j
public class AfterSaleServiceImpl extends ServiceImpl<AfterSaleMapper, AfterSale> implements AfterSaleService {

    private static final int REASON_MAX_LENGTH = 500;
    private static final int REJECT_REASON_MAX_LENGTH = 200;
    private static final int RETRY_BATCH_SIZE = 50;

    @Autowired
    private AfterSaleMapper afterSaleMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PayRecordMapper payRecordMapper;

    @Autowired
    private OrderStockRestoreMsgMapper orderStockRestoreMsgMapper;

    @Autowired
    private MockPayGatewayClient gatewayClient;

    @Override
    public ResponseDto<AfterSale> apply(Integer uId, AfterSaleApplyRequest request) {
        if (request == null || request.getOId() == null) {
            return ResponseDto.error("订单ID不能为空");
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isEmpty()) {
            return ResponseDto.error("请填写申请原因");
        }
        if (reason.length() > REASON_MAX_LENGTH) {
            return ResponseDto.error("申请原因不能超过 " + REASON_MAX_LENGTH + " 字");
        }
        Order order = orderMapper.selectById(request.getOId());
        // 越权与不存在给同一句提示，不向外暴露他人订单是否存在（同 ReviewServiceImpl）
        if (order == null || !uId.equals(order.getUId())) {
            return ResponseDto.error("订单不存在");
        }
        Integer orderStatus = order.getOrderStatus();
        if (!COMPLETE_ORDER_STATUS.equals(orderStatus) && !SHIPPED_ORDER_STATUS.equals(orderStatus)) {
            return ResponseDto.error("已发货或已完成的订单才能申请售后");
        }

        AfterSale record = new AfterSale();
        record.setOId(order.getOId());
        record.setUId(uId);
        record.setType(request.getType() == null ? AfterSale.TYPE_REFUND : request.getType());
        record.setReason(reason);
        record.setStatus(AfterSale.STATUS_PENDING);
        record.setRefundAmount(order.getOrderAmount());
        record.setCreateTime(new Date());
        record.setVersion(0);
        try {
            afterSaleMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 唯一键 uk_o_id 是权威判据：被拒绝/已取消的工单允许复用同一行重新申请
            int rows = afterSaleMapper.reapply(order.getOId(), uId,
                    record.getType(), reason, order.getOrderAmount());
            if (rows == 0) {
                return ResponseDto.error("该订单已有售后申请在处理中，请勿重复提交");
            }
        }
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<AfterSale> cancel(Integer uId, Integer id) {
        if (id == null) {
            return ResponseDto.error("售后单ID不能为空");
        }
        AfterSale record = afterSaleMapper.selectById(id);
        if (record == null || !uId.equals(record.getUId())) {
            return ResponseDto.error("售后单不存在");
        }
        if (AfterSale.STATUS_CANCELLED == record.getStatus()) {
            return ResponseDto.success(null);
        }
        if (AfterSale.STATUS_PENDING != record.getStatus()) {
            return ResponseDto.error("仅待审核的售后申请可以撤销");
        }
        int rows = afterSaleMapper.casCancel(id, record.getVersion());
        if (rows == 0) {
            return ResponseDto.error("售后单状态已变更，请刷新后重试");
        }
        return ResponseDto.success(null);
    }

    /**
     * 商家审核。同意分支同一本地事务内：工单 CAS 0→1 + 支付单 CAS 1→4 + 写库存回补消息(source=1)，
     * 事务提交后 best-effort 调网关退款（同 PayServiceImpl.registerRefundAfterCommit 模式）；
     * 退款失败工单停在 1，由 retryRefund 兜底重试。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseDto<AfterSale> audit(Integer id, boolean approve, String rejectReason, OrderScope scope) {
        if (id == null) {
            return ResponseDto.error("售后单ID不能为空");
        }
        // 顾客无审核权限；商家/管理员/内部调用 scope 为 unrestricted
        if (!scope.isUnrestricted()) {
            return ResponseDto.error("无权审核售后申请");
        }
        AfterSale record = afterSaleMapper.selectById(id);
        if (record == null) {
            return ResponseDto.error("售后单不存在");
        }
        if (AfterSale.STATUS_PENDING != record.getStatus()) {
            return ResponseDto.error("该售后单已处理，请刷新查看");
        }

        if (!approve) {
            String reject = rejectReason == null ? "" : rejectReason.trim();
            if (reject.isEmpty()) {
                return ResponseDto.error("请填写拒绝原因");
            }
            if (reject.length() > REJECT_REASON_MAX_LENGTH) {
                return ResponseDto.error("拒绝原因不能超过 " + REJECT_REASON_MAX_LENGTH + " 字");
            }
            int rows = afterSaleMapper.casReject(id, record.getVersion(), reject);
            return rows > 0 ? ResponseDto.success(null)
                    : ResponseDto.error("售后单状态已变更，请刷新后重试");
        }

        // 同意：先锁定退款依据 —— 该订单成功支付的支付单
        PayRecord payRecord = payRecordMapper.selectOne(new QueryWrapper<PayRecord>()
                .eq("o_id", record.getOId()).eq("status", PayRecord.STATUS_SUCCESS)
                .orderByDesc("id").last("LIMIT 1"));
        if (payRecord == null) {
            return ResponseDto.error("该订单无成功支付记录，无法自动退款");
        }
        int rows = afterSaleMapper.casApprove(id, record.getVersion(), payRecord.getPayNo());
        if (rows == 0) {
            return ResponseDto.error("售后单状态已变更，请刷新后重试");
        }
        int payRows = payRecordMapper.casUpdateStatus(payRecord.getPayNo(),
                PayRecord.STATUS_SUCCESS, PayRecord.STATUS_REFUNDING);
        if (payRows == 0) {
            // 支付单被并发推进（理论上不该发生），整体回滚保持工单待审核可重试
            throw new RuntimeException("支付单状态异常，退款发起失败 payNo=" + payRecord.getPayNo());
        }
        // 同事务写库存回补消息，由 StockRestoreMsgConsumer 异步回库存；source=1 标记售后来源（不删订单明细）
        OrderStockRestoreMsg msg = new OrderStockRestoreMsg();
        msg.setOId(record.getOId());
        msg.setSource(1);
        msg.setStatus(0);
        msg.setRetryCnt(0);
        msg.setMaxRetry(5);
        msg.setNextRetry(new Date());
        try {
            orderStockRestoreMsgMapper.insert(msg);
        } catch (DuplicateKeyException dupEx) {
            log.info("售后订单 {} 回库存消息已存在，跳过写入", record.getOId());
        }
        final Integer afterSaleId = id;
        final String payNo = payRecord.getPayNo();
        final String transactionId = payRecord.getTransactionId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executeRefund(afterSaleId, payNo, transactionId);
            }
        });
        return ResponseDto.success(null);
    }

    /**
     * 调网关退款并推进终态：支付单 4→5、工单 1→2。失败只记日志，等 retryRefund 重试。
     */
    private void executeRefund(Integer afterSaleId, String payNo, String transactionId) {
        if (gatewayClient.refund(transactionId)) {
            payRecordMapper.casUpdateStatus(payNo, PayRecord.STATUS_REFUNDING, PayRecord.STATUS_REFUNDED);
            afterSaleMapper.casFinishRefund(afterSaleId);
            log.info("[AfterSale] 售后退款完成 id={} payNo={}", afterSaleId, payNo);
        } else {
            log.warn("[AfterSale] 售后退款失败，等待重试 id={} payNo={}", afterSaleId, payNo);
        }
    }

    @Override
    public ResponseDto<AfterSale> pageMine(Integer uId, Integer status, int pageNo, int pageSize) {
        Page<AfterSale> page = new Page<>(pageNo, pageSize);
        afterSaleMapper.selectPageWithOrder(page, uId, status);
        return ResponseDto.success(page);
    }

    @Override
    public ResponseDto<AfterSale> pageAll(Integer status, int pageNo, int pageSize, OrderScope scope) {
        if (!scope.isUnrestricted()) {
            return ResponseDto.error("无权查看售后工单列表");
        }
        Page<AfterSale> page = new Page<>(pageNo, pageSize);
        afterSaleMapper.selectPageWithOrder(page, null, status);
        return ResponseDto.success(page);
    }

    @Override
    public ResponseDto<AfterSale> getByOrder(Integer oId, OrderScope scope) {
        if (oId == null) {
            return ResponseDto.error("订单ID不能为空");
        }
        AfterSale record = afterSaleMapper.selectOne(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getOId, oId).last("LIMIT 1"));
        if (record == null || !scope.canManage(record.getUId())) {
            return ResponseDto.success(null);
        }
        return ResponseDto.success(record);
    }

    /**
     * 兜底重试：扫"同意退款中(1)"的工单。支付单已是已退款(5)说明上次只差工单推进（进程宕在中间），
     * 直接补推工单；仍是待退款(4)则重调网关。
     */
    @Override
    public int retryRefund() {
        List<AfterSale> pending = afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getStatus, AfterSale.STATUS_REFUNDING)
                .orderByAsc(AfterSale::getId)
                .last("LIMIT " + RETRY_BATCH_SIZE));
        int processed = 0;
        for (AfterSale record : pending) {
            try {
                PayRecord payRecord = payRecordMapper.selectOne(
                        new QueryWrapper<PayRecord>().eq("pay_no", record.getRefundNo()));
                if (payRecord == null) {
                    log.error("[AfterSale] 工单 {} 找不到支付单 {}，需人工介入", record.getId(), record.getRefundNo());
                    continue;
                }
                if (payRecord.getStatus() == PayRecord.STATUS_REFUNDED) {
                    afterSaleMapper.casFinishRefund(record.getId());
                    processed++;
                    continue;
                }
                if (payRecord.getStatus() == PayRecord.STATUS_REFUNDING) {
                    executeRefund(record.getId(), payRecord.getPayNo(), payRecord.getTransactionId());
                    processed++;
                }
            } catch (Exception e) {
                log.error("[AfterSale] 重试退款异常 id={}", record.getId(), e);
            }
        }
        return processed;
    }
}
