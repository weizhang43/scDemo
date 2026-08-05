package com.example.scorder.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建支付单返回：前端据 transactionId 跳收银台，据 payNo 轮询状态。
 */
@Data
public class PayCreateVO {

    private String payNo;

    private String transactionId;

    private BigDecimal amount;

    /** 前端收银台路由路径 */
    private String cashierUrl;
}
