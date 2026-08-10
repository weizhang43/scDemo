package com.example.scpay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.curry.model.pay.PaySignUtil;
import com.example.scpay.entity.MockPayTxn;
import com.example.scpay.mapper.MockPayTxnMapper;
import com.example.scpay.notify.NotifySender;
import com.example.scpay.service.MockPayService;
import exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class MockPayServiceImpl implements MockPayService {

    private static final long TIMESTAMP_WINDOW_MS = 5 * 60 * 1000L;
    /** 交易号中随机段长度 */
    private static final int TXN_RANDOM_LEN = 8;
    /** 交易单不存在提示 */
    private static final String MSG_TXN_NOT_FOUND = "交易单不存在";
    /** 请求参数名：渠道交易号 */
    private static final String PARAM_TRANSACTION_ID = "transactionId";
    /** 模拟支付结果 */
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAIL = "FAIL";

    @Autowired
    private MockPayTxnMapper txnMapper;

    @Autowired
    private NotifySender notifySender;

    @Value("${pay.secret}")
    private String paySecret;

    /**
     * 商户签名接口统一入口校验：验签 + 时间窗。失败抛 BusinessException。
     */
    private void checkSign(Map<String, String> params) {
        if (!PaySignUtil.verify(params, paySecret)) {
            throw new BusinessException("验签失败");
        }
        long ts;
        try {
            ts = Long.parseLong(params.get("timestamp"));
        } catch (Exception e) {
            throw new BusinessException("timestamp 非法", e);
        }
        if (Math.abs(System.currentTimeMillis() - ts) > TIMESTAMP_WINDOW_MS) {
            throw new BusinessException("请求已过期");
        }
    }

    @Override
    public MockPayTxn precreate(Map<String, String> params) {
        checkSign(params);
        String payNo = params.get("payNo");
        String amountStr = params.get("amount");
        String notifyUrl = params.get("notifyUrl");
        if (payNo == null || payNo.isEmpty() || notifyUrl == null || notifyUrl.isEmpty()) {
            throw new BusinessException("payNo/notifyUrl 不能为空");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
        } catch (Exception e) {
            throw new BusinessException("amount 非法", e);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("amount 必须大于 0");
        }

        MockPayTxn exist = getByPayNo(payNo);
        if (exist != null) {
            return exist;
        }

        MockPayTxn txn = new MockPayTxn();
        txn.setTransactionId(generateTransactionId());
        txn.setPayNo(payNo);
        txn.setAmount(amount);
        txn.setSubject(params.get("subject"));
        txn.setStatus(MockPayTxn.STATUS_PENDING);
        txn.setNotifyUrl(notifyUrl);
        txn.setNotifyCnt(0);
        txn.setCreateTime(new Date());
        try {
            txnMapper.insert(txn);
        } catch (DuplicateKeyException e) {
            // 并发预下单撞 uk_pay_no：回读已有交易单幂等返回
            return getByPayNo(payNo);
        }
        return txn;
    }

    @Override
    public MockPayTxn query(Map<String, String> params) {
        checkSign(params);
        String transactionId = params.get(PARAM_TRANSACTION_ID);
        if (transactionId != null && !transactionId.isEmpty()) {
            return getByTransactionId(transactionId);
        }
        String payNo = params.get("payNo");
        if (payNo != null && !payNo.isEmpty()) {
            return getByPayNo(payNo);
        }
        throw new BusinessException("transactionId/payNo 至少传一个");
    }

    @Override
    public MockPayTxn closeTxn(Map<String, String> params) {
        checkSign(params);
        String transactionId = params.get(PARAM_TRANSACTION_ID);
        MockPayTxn txn = getByTransactionId(transactionId);
        if (txn == null) {
            throw new BusinessException(MSG_TXN_NOT_FOUND);
        }
        // CAS 0→3，已终态原样返回（幂等）
        txnMapper.casUpdateStatus(transactionId, MockPayTxn.STATUS_PENDING, MockPayTxn.STATUS_CLOSED);
        return getByTransactionId(transactionId);
    }

    @Override
    public MockPayTxn refund(Map<String, String> params) {
        checkSign(params);
        String transactionId = params.get(PARAM_TRANSACTION_ID);
        MockPayTxn txn = getByTransactionId(transactionId);
        if (txn == null) {
            throw new BusinessException(MSG_TXN_NOT_FOUND);
        }
        if (txn.getStatus() != MockPayTxn.STATUS_SUCCESS) {
            throw new BusinessException("交易未成功，无法退款");
        }
        if (txn.getRefundTime() == null) {
            MockPayTxn upd = new MockPayTxn();
            upd.setId(txn.getId());
            upd.setRefundTime(new Date());
            txnMapper.updateById(upd);
        }
        return getByTransactionId(transactionId);
    }

    @Override
    public MockPayTxn getByTransactionId(String transactionId) {
        if (transactionId == null || transactionId.isEmpty()) {
            return null;
        }
        return txnMapper.selectOne(new QueryWrapper<MockPayTxn>().eq("transaction_id", transactionId));
    }

    @Override
    public MockPayTxn simulate(String transactionId, String result) {
        boolean success = RESULT_SUCCESS.equalsIgnoreCase(result);
        if (!success && !RESULT_FAIL.equalsIgnoreCase(result)) {
            throw new BusinessException("result 只支持 SUCCESS/FAIL");
        }
        MockPayTxn txn = getByTransactionId(transactionId);
        if (txn == null) {
            throw new BusinessException(MSG_TXN_NOT_FOUND);
        }
        int target = success ? MockPayTxn.STATUS_SUCCESS : MockPayTxn.STATUS_FAIL;
        int rows = txnMapper.casUpdateStatus(transactionId, MockPayTxn.STATUS_PENDING, target);
        if (rows == 0) {
            // 已被支付/关闭等抢先推进，直接返回当前态（幂等），不重复触发回调
            return getByTransactionId(transactionId);
        }
        MockPayTxn updated = getByTransactionId(transactionId);
        notifySender.scheduleNotify(updated, success ? RESULT_SUCCESS : RESULT_FAIL);
        return updated;
    }

    @Override
    public MockPayTxn renotify(String transactionId) {
        MockPayTxn txn = getByTransactionId(transactionId);
        if (txn == null) {
            throw new BusinessException(MSG_TXN_NOT_FOUND);
        }
        if (txn.getStatus() != MockPayTxn.STATUS_SUCCESS && txn.getStatus() != MockPayTxn.STATUS_FAIL) {
            throw new BusinessException("交易未到终态，无法重发回调");
        }
        notifySender.scheduleNotify(txn,
                txn.getStatus() == MockPayTxn.STATUS_SUCCESS ? RESULT_SUCCESS : RESULT_FAIL);
        return txn;
    }

    private MockPayTxn getByPayNo(String payNo) {
        return txnMapper.selectOne(new QueryWrapper<MockPayTxn>().eq("pay_no", payNo));
    }

    private String generateTransactionId() {
        return "MTX" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, TXN_RANDOM_LEN).toUpperCase();
    }
}
