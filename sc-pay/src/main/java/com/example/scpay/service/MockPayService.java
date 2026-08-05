package com.example.scpay.service;

import com.example.scpay.entity.MockPayTxn;

import java.util.Map;

public interface MockPayService {

    /**
     * 商户预下单：验签后按 payNo 幂等创建交易单，返回已有或新建的交易单。
     */
    MockPayTxn precreate(Map<String, String> params);

    /**
     * 商户查询：验签后按 transactionId 或 payNo 查交易单，不存在返回 null。
     */
    MockPayTxn query(Map<String, String> params);

    /**
     * 商户关单：验签后 CAS 0→3。已终态则原样返回（幂等）。
     */
    MockPayTxn closeTxn(Map<String, String> params);

    /**
     * 商户退款：验签后要求交易单为成功态，记退款时间（记账，不改 status）。
     */
    MockPayTxn refund(Map<String, String> params);

    /**
     * 收银台展示：按交易号取交易单，不存在返回 null。
     */
    MockPayTxn getByTransactionId(String transactionId);

    /**
     * 收银台模拟支付：CAS 0→1/2，成功后调度异步回调。
     * @param result SUCCESS 或 FAIL
     */
    MockPayTxn simulate(String transactionId, String result);

    /**
     * 手动重发回调（演示商户侧幂等），交易单须已是终态 1/2。
     */
    MockPayTxn renotify(String transactionId);
}
