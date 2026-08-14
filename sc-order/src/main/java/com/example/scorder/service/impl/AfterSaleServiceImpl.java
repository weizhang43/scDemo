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
import com.example.scorder.util.ImageUrlUtil;
import exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import response.ResponseDto;

import java.util.Date;
import java.util.List;

import static com.example.scorder.service.impl.OrderServiceImpl.COMPLETE_ORDER_STATUS;
import static com.example.scorder.service.impl.OrderServiceImpl.SHIPPED_ORDER_STATUS;

/**
 * 售后服务实现：顾客申请/撤销、商家审核（同意走自动退款 + 库存回补消息）、退款兜底重试。
 * 状态推进全部走 CAS，竞态时提示刷新重试。
 */
@Service
public class AfterSaleServiceImpl extends ServiceImpl<AfterSaleMapper, AfterSale> implements AfterSaleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AfterSaleServiceImpl.class);

    /** 申请原因最大长度 */
    private static final int REASON_MAX_LENGTH = 500;

    /** 拒绝原因最大长度 */
    private static final int REJECT_REASON_MAX_LENGTH = 200;

    /** 凭证图片最多张数 */
    private static final int IMAGES_MAX_COUNT = 3;

    /** 退款兜底重试单批扫描条数 */
    private static final int RETRY_BATCH_SIZE = 50;

    /** CAS 推进失败（并发修改）的统一提示 */
    private static final String MSG_STATUS_CHANGED = "售后单状态已变更，请刷新后重试";

    /** 库存回补消息最大重试次数 */
    private static final int STOCK_RESTORE_MAX_RETRY = 5;

    /** 库存回补消息来源：1=售后（不删订单明细） */
    private static final int RESTORE_SOURCE_AFTER_SALE = 1;

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

    /**
     * 顾客申请售后：校验订单可申请后落工单；唯一键冲突时复用被拒绝/已取消的旧工单重新申请。
     */
    @Override
    public ResponseDto<AfterSale> apply(Integer uId, AfterSaleApplyRequest request) {
        Order order = loadApplicableOrder(uId, request);
        String reason = request.getReason().trim();
        AfterSale record = buildApplyRecord(uId, request, order, reason);
        try {
            afterSaleMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 唯一键 uk_o_id 是权威判据：被拒绝/已取消的工单允许复用同一行重新申请
            LOGGER.info("售后订单 {} 已有工单，尝试复用重新申请", order.getOId(), e);
            int rows = afterSaleMapper.reapply(order.getOId(), uId,
                    record.getType(), reason, record.getImages(), order.getOrderAmount());
            if (rows == 0) {
                return ResponseDto.error("该订单已有售后申请在处理中，请勿重复提交");
            }
        }
        return ResponseDto.success(null);
    }

    /**
     * 校验申请参数与订单归属/状态，不满足抛业务异常；通过则返回订单。
     */
    private Order loadApplicableOrder(Integer uId, AfterSaleApplyRequest request) {
        if (request == null || request.getOId() == null) {
            throw new BusinessException("订单ID不能为空");
        }
        validateReason(request.getReason());
        Order order = orderMapper.selectById(request.getOId());
        // 越权与不存在给同一句提示，不向外暴露他人订单是否存在（同 ReviewServiceImpl）
        if (order == null || !uId.equals(order.getUId())) {
            throw new BusinessException("订单不存在");
        }
        Integer orderStatus = order.getOrderStatus();
        if (!COMPLETE_ORDER_STATUS.equals(orderStatus) && !SHIPPED_ORDER_STATUS.equals(orderStatus)) {
            throw new BusinessException("已发货或已完成的订单才能申请售后");
        }
        return order;
    }

    /**
     * 校验申请原因非空且长度合规。
     */
    private void validateReason(String rawReason) {
        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.isEmpty()) {
            throw new BusinessException("请填写申请原因");
        }
        if (reason.length() > REASON_MAX_LENGTH) {
            throw new BusinessException("申请原因不能超过 " + REASON_MAX_LENGTH + " 字");
        }
    }

    /**
     * 组装售后工单（退款金额取订单实付额，初始待审核）。
     */
    private AfterSale buildApplyRecord(Integer uId, AfterSaleApplyRequest request, Order order, String reason) {
        AfterSale record = new AfterSale();
        record.setOId(order.getOId());
        record.setUId(uId);
        record.setType(request.getType() == null ? AfterSale.TYPE_REFUND : request.getType());
        record.setReason(reason);
        record.setImages(ImageUrlUtil.validateAndJoin(request.getImages(), IMAGES_MAX_COUNT));
        record.setStatus(AfterSale.STATUS_PENDING);
        record.setRefundAmount(order.getOrderAmount());
        record.setCreateTime(new Date());
        record.setVersion(0);
        return record;
    }

    /**
     * 顾客撤销售后申请：已撤销幂等成功；仅待审核可撤，CAS 失败提示刷新。
     */
    @Override
    public ResponseDto<AfterSale> cancel(Integer uId, Integer id) {
        AfterSale record = loadOwnAfterSale(uId, id);
        if (AfterSale.STATUS_CANCELLED == record.getStatus()) {
            return ResponseDto.success(null);
        }
        if (AfterSale.STATUS_PENDING != record.getStatus()) {
            throw new BusinessException("仅待审核的售后申请可以撤销");
        }
        int rows = afterSaleMapper.casCancel(id, record.getVersion());
        return rows > 0 ? ResponseDto.success(null) : ResponseDto.error(MSG_STATUS_CHANGED);
    }

    /**
     * 加载本人售后单，缺失或越权抛业务异常（同一句提示防探测）。
     */
    private AfterSale loadOwnAfterSale(Integer uId, Integer id) {
        if (id == null) {
            throw new BusinessException("售后单ID不能为空");
        }
        AfterSale record = afterSaleMapper.selectById(id);
        if (record == null || !uId.equals(record.getUId())) {
            throw new BusinessException("售后单不存在");
        }
        return record;
    }

    /**
     * 商家审核。同意分支同一本地事务内：工单 CAS 0→1 + 支付单 CAS 1→4 + 写库存回补消息(source=1)，
     * 事务提交后 best-effort 调网关退款（同 PayServiceImpl.registerRefundAfterCommit 模式）；
     * 退款失败工单停在 1，由 retryRefund 兜底重试。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseDto<AfterSale> audit(Integer id, boolean approve, String rejectReason, OrderScope scope) {
        AfterSale record = loadAuditable(id, scope);
        if (!approve) {
            return doReject(id, record, rejectReason);
        }
        return doApprove(id, record);
    }

    /**
     * 审核前校验：权限、存在性与待审核状态，任一不满足抛业务异常。
     */
    private AfterSale loadAuditable(Integer id, OrderScope scope) {
        if (id == null) {
            throw new BusinessException("售后单ID不能为空");
        }
        // 顾客无审核权限；商家/管理员/内部调用 scope 为 unrestricted
        if (!scope.isUnrestricted()) {
            throw new BusinessException("无权审核售后申请");
        }
        AfterSale record = afterSaleMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("售后单不存在");
        }
        if (AfterSale.STATUS_PENDING != record.getStatus()) {
            throw new BusinessException("该售后单已处理，请刷新查看");
        }
        return record;
    }

    /**
     * 拒绝分支：校验拒绝原因后 CAS 0→3。
     */
    private ResponseDto<AfterSale> doReject(Integer id, AfterSale record, String rejectReason) {
        String reject = rejectReason == null ? "" : rejectReason.trim();
        if (reject.isEmpty()) {
            throw new BusinessException("请填写拒绝原因");
        }
        if (reject.length() > REJECT_REASON_MAX_LENGTH) {
            throw new BusinessException("拒绝原因不能超过 " + REJECT_REASON_MAX_LENGTH + " 字");
        }
        int rows = afterSaleMapper.casReject(id, record.getVersion(), reject);
        return rows > 0 ? ResponseDto.success(null) : ResponseDto.error(MSG_STATUS_CHANGED);
    }

    /**
     * 同意分支：锁定成功支付单 → 工单 CAS 0→1 → 支付单 CAS 1→4 → 写库存回补消息 →
     * 注册提交后退款。支付单 CAS 失败抛异常整体回滚，保持工单待审核可重试。
     */
    private ResponseDto<AfterSale> doApprove(Integer id, AfterSale record) {
        // 同意：先锁定退款依据 —— 该订单成功支付的支付单
        PayRecord payRecord = payRecordMapper.selectOne(new QueryWrapper<PayRecord>()
                .eq("o_id", record.getOId()).eq("status", PayRecord.STATUS_SUCCESS)
                .orderByDesc("id").last("LIMIT 1"));
        if (payRecord == null) {
            throw new BusinessException("该订单无成功支付记录，无法自动退款");
        }
        int rows = afterSaleMapper.casApprove(id, record.getVersion(), payRecord.getPayNo());
        if (rows == 0) {
            return ResponseDto.error(MSG_STATUS_CHANGED);
        }
        int payRows = payRecordMapper.casUpdateStatus(payRecord.getPayNo(),
                PayRecord.STATUS_SUCCESS, PayRecord.STATUS_REFUNDING);
        if (payRows == 0) {
            // 支付单被并发推进（理论上不该发生），整体回滚保持工单待审核可重试
            throw new BusinessException("支付单状态异常，退款发起失败 payNo=" + payRecord.getPayNo());
        }
        insertRestoreMsg(record.getOId());
        registerRefundAfterCommit(id, payRecord);
        return ResponseDto.success(null);
    }

    /**
     * 同事务写库存回补消息，由 StockRestoreMsgConsumer 异步回库存；source=1 标记售后来源（不删订单明细）。
     */
    private void insertRestoreMsg(Integer oId) {
        OrderStockRestoreMsg msg = new OrderStockRestoreMsg();
        msg.setOId(oId);
        msg.setSource(RESTORE_SOURCE_AFTER_SALE);
        msg.setStatus(0);
        msg.setRetryCnt(0);
        msg.setMaxRetry(STOCK_RESTORE_MAX_RETRY);
        msg.setNextRetry(new Date());
        try {
            orderStockRestoreMsgMapper.insert(msg);
        } catch (DuplicateKeyException dupEx) {
            LOGGER.info("售后订单 {} 回库存消息已存在，跳过写入", oId, dupEx);
        }
    }

    /**
     * 注册事务提交后的自动退款动作（提交前不外呼网关，避免回滚后钱已退）。
     */
    private void registerRefundAfterCommit(Integer afterSaleId, PayRecord payRecord) {
        final String payNo = payRecord.getPayNo();
        final String transactionId = payRecord.getTransactionId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executeRefund(afterSaleId, payNo, transactionId);
            }
        });
    }

    /**
     * 调网关退款并推进终态：支付单 4→5、工单 1→2。失败只记日志，等 retryRefund 重试。
     */
    private void executeRefund(Integer afterSaleId, String payNo, String transactionId) {
        if (gatewayClient.refund(transactionId)) {
            payRecordMapper.casUpdateStatus(payNo, PayRecord.STATUS_REFUNDING, PayRecord.STATUS_REFUNDED);
            afterSaleMapper.casFinishRefund(afterSaleId);
            LOGGER.info("[AfterSale] 售后退款完成 id={} payNo={}", afterSaleId, payNo);
        } else {
            LOGGER.warn("[AfterSale] 售后退款失败，等待重试 id={} payNo={}", afterSaleId, payNo);
        }
    }

    /**
     * 顾客分页查询自己的售后单（联订单信息）。
     */
    @Override
    public ResponseDto<AfterSale> pageMine(Integer uId, Integer status, int pageNo, int pageSize) {
        Page<AfterSale> page = new Page<>(pageNo, pageSize);
        afterSaleMapper.selectPageWithOrder(page, uId, status);
        return ResponseDto.success(page);
    }

    /**
     * 商家/管理员分页查询全部售后单，顾客无权。
     */
    @Override
    public ResponseDto<AfterSale> pageAll(Integer status, int pageNo, int pageSize, OrderScope scope) {
        if (!scope.isUnrestricted()) {
            return ResponseDto.error("无权查看售后工单列表");
        }
        Page<AfterSale> page = new Page<>(pageNo, pageSize);
        afterSaleMapper.selectPageWithOrder(page, null, status);
        return ResponseDto.success(page);
    }

    /**
     * 按订单查询售后单，越权或不存在返回空数据（前端按无售后处理）。
     */
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
                processed += retryOne(record);
            } catch (Exception e) {
                LOGGER.error("[AfterSale] 重试退款异常 id={}", record.getId(), e);
            }
        }
        return processed;
    }

    /**
     * 单条工单的退款重试；处理到位返回 1，跳过返回 0。
     */
    private int retryOne(AfterSale record) {
        PayRecord payRecord = payRecordMapper.selectOne(
                new QueryWrapper<PayRecord>().eq("pay_no", record.getRefundNo()));
        if (payRecord == null) {
            LOGGER.error("[AfterSale] 工单 {} 找不到支付单 {}，需人工介入", record.getId(), record.getRefundNo());
            return 0;
        }
        int processed = 0;
        if (payRecord.getStatus() == PayRecord.STATUS_REFUNDED) {
            // 上次只差工单推进（进程宕在中间），直接补推工单
            afterSaleMapper.casFinishRefund(record.getId());
            processed = 1;
        }
        if (payRecord.getStatus() == PayRecord.STATUS_REFUNDING) {
            executeRefund(record.getId(), payRecord.getPayNo(), payRecord.getTransactionId());
            processed = 1;
        }
        return processed;
    }
}
