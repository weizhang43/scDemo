package com.example.scorder.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.curry.model.Order;
import com.curry.model.pay.PaySignUtil;
import com.example.scorder.auth.OrderScope;
import com.example.scorder.client.MockPayGatewayClient;
import com.example.scorder.entity.PayNotifyLog;
import com.example.scorder.entity.PayRecord;
import com.example.scorder.mapper.OrderMapper;
import com.example.scorder.mapper.PayNotifyLogMapper;
import com.example.scorder.mapper.PayRecordMapper;
import com.example.scorder.service.PayService;
import com.example.scorder.vo.PayCreateVO;
import com.example.scorder.vo.PayStatusVO;
import exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import response.ResponseDto;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.example.scorder.service.impl.OrderServiceImpl.PLACED_ORDER_STATUS;
import static com.example.scorder.service.impl.OrderServiceImpl.UN_COMMIT_ORDER_STATUS;

@Slf4j
@Service
public class PayServiceImpl implements PayService {

    private static final long TIMESTAMP_WINDOW_MS = 5 * 60 * 1000L;
    private static final String DEFAULT_CHANNEL = "MOCK";

    @Autowired
    private PayRecordMapper payRecordMapper;

    @Autowired
    private PayNotifyLogMapper payNotifyLogMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private MockPayGatewayClient gatewayClient;

    @Autowired
    private com.example.scorder.client.CouponClient couponClient;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${pay.secret:sc-pay-secret-dev}")
    private String paySecret;

    @Override
    public ResponseDto<PayCreateVO> createPay(Integer oId, String channel, OrderScope scope) {
        if (oId == null) {
            return ResponseDto.error("订单ID不能为空");
        }
        Order order = orderMapper.selectById(oId);
        // 越权与不存在返回同一句提示，避免顾客探测他人订单
        if (order == null || !scope.canManage(order.getUId())) {
            return ResponseDto.error("订单不存在");
        }
        if (!Objects.equals(order.getOrderStatus(), UN_COMMIT_ORDER_STATUS)) {
            return ResponseDto.error("订单不是待付款状态");
        }
        RLock lock = redissonClient.getLock("lock:pay:create:" + oId);
        boolean locked;
        try {
            // 不指定 leaseTime，走 watchdog 自动续期：锁内有网关外呼，固定租约会在慢调用时提前失效
            locked = lock.tryLock(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseDto.error("系统繁忙，请稍后重试");
        }
        if (!locked) {
            return ResponseDto.error("操作过于频繁，请稍后重试");
        }
        try {
            // 每订单一张在途单：已有 status=0 的支付单则幂等复用（双击/重进收银台拿同一单）
            PayRecord exist = payRecordMapper.selectOne(new QueryWrapper<PayRecord>()
                    .eq("o_id", oId).eq("status", PayRecord.STATUS_PENDING)
                    .orderByDesc("id").last("LIMIT 1"));
            if (exist != null) {
                if (exist.getTransactionId() == null || exist.getTransactionId().isEmpty()) {
                    // 上次预下单未回填成功：网关按 payNo 幂等，重调补上
                    backfillTransactionId(exist, order);
                }
                return ResponseDto.success(toCreateVO(exist));
            }

            PayRecord record = new PayRecord();
            record.setPayNo(generatePayNo());
            record.setOId(oId);
            record.setOrderNo(order.getOrderNo());
            record.setUId(order.getUId());
            record.setAmount(order.getOrderAmount());
            record.setChannel(channel == null || channel.isEmpty() ? DEFAULT_CHANNEL : channel);
            record.setStatus(PayRecord.STATUS_PENDING);
            record.setCreateTime(new Date());
            record.setVersion(0);
            payRecordMapper.insert(record);

            backfillTransactionId(record, order);
            return ResponseDto.success(toCreateVO(record));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void backfillTransactionId(PayRecord record, Order order) {
        String transactionId = gatewayClient.precreate(record.getPayNo(), record.getAmount(),
                "订单 " + order.getOrderNo());
        PayRecord upd = new PayRecord();
        upd.setId(record.getId());
        upd.setTransactionId(transactionId);
        payRecordMapper.updateById(upd);
        record.setTransactionId(transactionId);
    }

    private PayCreateVO toCreateVO(PayRecord record) {
        PayCreateVO vo = new PayCreateVO();
        vo.setPayNo(record.getPayNo());
        vo.setTransactionId(record.getTransactionId());
        vo.setAmount(record.getAmount());
        vo.setCashierUrl("/cashier/" + record.getTransactionId());
        return vo;
    }

    @Override
    public ResponseDto<PayStatusVO> getStatus(String payNo, OrderScope scope) {
        PayRecord record = getByPayNo(payNo);
        if (record == null || !scope.canManage(record.getUId())) {
            return ResponseDto.error("支付单不存在");
        }
        PayStatusVO vo = new PayStatusVO();
        vo.setPayNo(record.getPayNo());
        vo.setOId(record.getOId());
        vo.setStatus(record.getStatus());
        return ResponseDto.success(vo);
    }

    /**
     * 处理结果：ack=true 时向网关返回 "success"（无需重试），desc 落 t_pay_notify_log。
     */
    private static final class Outcome {
        final boolean ack;
        final String desc;
        Outcome(boolean ack, String desc) {
            this.ack = ack;
            this.desc = desc;
        }
    }

    @Override
    public String handleNotify(Map<String, String> params) {
        boolean signOk = PaySignUtil.verify(params, paySecret);
        Outcome outcome;
        if (!signOk) {
            outcome = new Outcome(false, "验签失败");
        } else {
            try {
                outcome = doHandleNotify(params);
            } catch (BusinessException e) {
                outcome = new Outcome(false, e.getMessage());
            } catch (Exception e) {
                log.error("[PayNotify] 回调处理异常 params={}", params, e);
                outcome = new Outcome(false, "处理异常");
            }
        }
        insertNotifyLog(params, signOk, outcome.desc);
        return outcome.ack ? "success" : "fail";
    }

    private Outcome doHandleNotify(Map<String, String> params) {
        long ts;
        try {
            ts = Long.parseLong(params.get("timestamp"));
        } catch (Exception e) {
            return new Outcome(false, "timestamp 非法");
        }
        if (Math.abs(System.currentTimeMillis() - ts) > TIMESTAMP_WINDOW_MS) {
            return new Outcome(false, "回调已过期");
        }
        String nonce = params.get("nonce");
        if (nonce == null || nonce.isEmpty()) {
            return new Outcome(false, "nonce 缺失");
        }
        // SETNX 防重放：原样重发的报文直接拒收；网关正常重试自带新 nonce
        RBucket<String> nonceBucket = redissonClient.getBucket("pay:nonce:" + nonce);
        if (!nonceBucket.trySet("1", 10, TimeUnit.MINUTES)) {
            return new Outcome(false, "nonce 重复，疑似重放");
        }

        String payNo = params.get("payNo");
        PayRecord record = getByPayNo(payNo);
        if (record == null) {
            return new Outcome(false, "支付单不存在");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(params.get("amount"));
        } catch (Exception e) {
            return new Outcome(false, "amount 非法");
        }
        if (record.getAmount().compareTo(amount) != 0) {
            log.warn("[PayNotify] 金额不符 payNo={}, 期望={}, 回调={}", payNo, record.getAmount(), amount);
            return new Outcome(false, "金额不符");
        }

        String tradeStatus = params.get("tradeStatus");
        if ("SUCCESS".equals(tradeStatus)) {
            return handleSuccess(record);
        }
        if ("FAIL".equals(tradeStatus)) {
            // 支付失败：支付单 0→2，订单保持待付款可重新发起；rows==0 说明已被推进，幂等确认
            payRecordMapper.casUpdateStatus(record.getPayNo(), PayRecord.STATUS_PENDING, PayRecord.STATUS_FAIL);
            return new Outcome(true, "支付失败已记录");
        }
        return new Outcome(false, "tradeStatus 非法: " + tradeStatus);
    }

    /**
     * 支付成功回调核心。同一本地事务内固定顺序：先支付单 CAS、后订单 CAS。
     * 竞态谁先 CAS 赢谁：取消先赢 → 支付单转待退款并在提交后调网关退款。
     */
    private Outcome handleSuccess(PayRecord record) {
        String payNo = record.getPayNo();
        return transactionTemplate.execute(tx -> {
            int rows = payRecordMapper.casUpdateStatusPaid(payNo, PayRecord.STATUS_PENDING, PayRecord.STATUS_SUCCESS);
            if (rows == 0) {
                PayRecord current = getByPayNo(payNo);
                if (current.getStatus() == PayRecord.STATUS_SUCCESS
                        || current.getStatus() == PayRecord.STATUS_REFUNDING
                        || current.getStatus() == PayRecord.STATUS_REFUNDED) {
                    return new Outcome(true, "重复回调，忽略");
                }
                if (current.getStatus() == PayRecord.STATUS_CLOSED) {
                    // 订单已超时取消（关单先赢），钱已被扣 → 转自动退款
                    int r = payRecordMapper.casUpdateStatus(payNo, PayRecord.STATUS_CLOSED, PayRecord.STATUS_REFUNDING);
                    if (r > 0) {
                        registerRefundAfterCommit(current);
                    }
                    return new Outcome(true, "订单已取消，转自动退款");
                }
                return new Outcome(true, "支付单已终态(status=" + current.getStatus() + ")，忽略");
            }

            // 支付单已推进为成功，尝试推进订单 0→1
            Order order = orderMapper.selectById(record.getOId());
            if (order != null && Objects.equals(order.getOrderStatus(), UN_COMMIT_ORDER_STATUS)) {
                int orderRows = orderMapper.casUpdateStatus(order.getOId(), UN_COMMIT_ORDER_STATUS,
                        PLACED_ORDER_STATUS, order.getVersion());
                if (orderRows > 0) {
                    registerCouponUseAfterCommit(order);
                    return new Outcome(true, "支付成功，订单已更新为已支付");
                }
                order = orderMapper.selectById(record.getOId());
            }
            if (order != null && Objects.equals(order.getOrderStatus(), PLACED_ORDER_STATUS)) {
                return new Outcome(true, "订单已是已支付，忽略");
            }
            // 订单被取消抢先（或异常态）：支付单 1→4，提交后调网关退款
            int r = payRecordMapper.casUpdateStatus(payNo, PayRecord.STATUS_SUCCESS, PayRecord.STATUS_REFUNDING);
            if (r > 0) {
                registerRefundAfterCommit(record);
            }
            Integer orderStatus = order == null ? null : order.getOrderStatus();
            log.warn("[PayNotify] 订单状态竞态 payNo={}, oId={}, orderStatus={}，支付单转退款",
                    payNo, record.getOId(), orderStatus);
            return new Outcome(true, "订单已取消(status=" + orderStatus + ")，支付款转自动退款");
        });
    }

    /**
     * 事务提交后 best-effort 核销优惠券(1→2)。失败留在已锁定：券归属该订单的事实
     * 在 t_order.coupon_id，锁定态不影响退款/返还链路，网关重试回调时幂等补核销。
     */
    private void registerCouponUseAfterCommit(Order order) {
        if (order.getCouponId() == null) {
            return;
        }
        final Integer couponId = order.getCouponId();
        final Integer oId = order.getOId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    ResponseDto<Object> resp = couponClient.use(couponId, oId);
                    if (resp == null || resp.getCode() == null || resp.getCode() != 200) {
                        log.warn("[PayNotify] 券核销未成功 couponId={}, oId={}, msg={}",
                                couponId, oId, resp == null ? null : resp.getMsg());
                    }
                } catch (Exception e) {
                    log.error("[PayNotify] 券核销调用失败 couponId={}, oId={}", couponId, oId, e);
                }
            }
        });
    }

    /**
     * 事务提交后 best-effort 调网关退款，成功则支付单 4→5；失败停在 4（待退款）人工介入。
     */
    private void registerRefundAfterCommit(PayRecord record) {
        final String payNo = record.getPayNo();
        final String transactionId = record.getTransactionId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (gatewayClient.refund(transactionId)) {
                    payRecordMapper.casUpdateStatus(payNo, PayRecord.STATUS_REFUNDING, PayRecord.STATUS_REFUNDED);
                    log.info("[PayNotify] 自动退款完成 payNo={}", payNo);
                }
            }
        });
    }

    @Override
    public PayRecord closePayForOrder(Integer oId) {
        PayRecord pending = payRecordMapper.selectOne(new QueryWrapper<PayRecord>()
                .eq("o_id", oId).eq("status", PayRecord.STATUS_PENDING)
                .orderByDesc("id").last("LIMIT 1"));
        if (pending == null) {
            return null;
        }
        int rows = payRecordMapper.casUpdateStatus(pending.getPayNo(),
                PayRecord.STATUS_PENDING, PayRecord.STATUS_CLOSED);
        return rows > 0 ? pending : null;
    }

    private PayRecord getByPayNo(String payNo) {
        if (payNo == null || payNo.isEmpty()) {
            return null;
        }
        return payRecordMapper.selectOne(new QueryWrapper<PayRecord>().eq("pay_no", payNo));
    }

    /**
     * 回调流水无条件落一条：在主事务外插入，处理回滚也留痕。
     */
    private void insertNotifyLog(Map<String, String> params, boolean signOk, String result) {
        try {
            PayNotifyLog logRow = new PayNotifyLog();
            logRow.setPayNo(params.get("payNo"));
            logRow.setTransactionId(params.get("transactionId"));
            logRow.setTradeStatus(params.get("tradeStatus"));
            String raw = JSON.toJSONString(params);
            logRow.setRawParams(raw.length() > 2000 ? raw.substring(0, 2000) : raw);
            logRow.setSignOk(signOk ? 1 : 0);
            logRow.setProcessResult(result);
            logRow.setCreateTime(new Date());
            payNotifyLogMapper.insert(logRow);
        } catch (Exception e) {
            log.error("[PayNotify] 回调流水落库失败", e);
        }
    }

    /**
     * 生成支付单号：PAY + yyyyMMdd + 流水号（当日 Redis 自增，至少 4 位），套用订单号模式。
     */
    private String generatePayNo() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dayPrefix = sdf.format(new Date());
        RAtomicLong seqCounter = redissonClient.getAtomicLong("pay:no:seq:" + dayPrefix);
        long seq = seqCounter.incrementAndGet();
        if (seq == 1) {
            seqCounter.expire(2, TimeUnit.DAYS);
        }
        DecimalFormat df = new DecimalFormat("0000");
        return "PAY" + dayPrefix + df.format(seq);
    }
}
