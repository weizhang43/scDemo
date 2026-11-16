package com.curry.model.pay;

import java.io.Serializable;

/**
 * 支付通知消息体：sc-pay 投递，sc-order 消费。
 *
 * 放在 sc-model 共享模块的原因：
 *  - sc-pay 是生产者（投递消息）；
 *  - sc-order 是消费者（监听消息）；
 *  - 两边都需要这个 POJO 来序列化/反序列化，放到 sc-model 让双方共享同一类型定义，
 *    避免字段不一致导致的反序列化失败。
 *
 * 字段对应原 NotifySender HTTP 回调表单参数的全部业务字段：
 *  - transactionId/tradeStatus/amount：业务核心
 *  - payNo：sc-order 回查支付单用
 *  - nonce：sc-order 侧 SETNX 防重放，每次重试必须换新
 *  - sign：sc-order 侧验签，保证消息未被篡改
 *
 * 不含 timestamp：HTTP 链路用它防过期，MQ 链路 nonce 即可；
 * 不含 notifyUrl：sc-order 已知道自己暴露的回调地址。
 *
 * 序列化：RocketMQ Spring 客户端用 JSON 序列化 POJO，消费端按同类型反序列化。
 */
public class PayNotifyMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 网关交易号（MockPayTxn.transactionId） */
    private String transactionId;
    /** 商户订单号（MockPayTxn.payNo） */
    private String payNo;
    /** 支付金额（字符串形式，避免 BigDecimal 序列化精度问题） */
    private String amount;
    /** 支付结果：SUCCESS / FAIL */
    private String tradeStatus;
    /** 防重放 nonce，每次发送/重试重新生成 */
    private String nonce;
    /** 签名值，sc-order 侧用 pay.secret 验签 */
    private String sign;

    public PayNotifyMessage() {
    }

    public PayNotifyMessage(String transactionId, String payNo, String amount,
                            String tradeStatus, String nonce, String sign) {
        this.transactionId = transactionId;
        this.payNo = payNo;
        this.amount = amount;
        this.tradeStatus = tradeStatus;
        this.nonce = nonce;
        this.sign = sign;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getPayNo() { return payNo; }
    public void setPayNo(String payNo) { this.payNo = payNo; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public String getTradeStatus() { return tradeStatus; }
    public void setTradeStatus(String tradeStatus) { this.tradeStatus = tradeStatus; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getSign() { return sign; }
    public void setSign(String sign) { this.sign = sign; }
}
