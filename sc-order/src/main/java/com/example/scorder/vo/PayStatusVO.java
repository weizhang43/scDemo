package com.example.scorder.vo;

import lombok.Data;

/**
 * 支付单状态轮询返回。status: 0-待支付 1-成功 2-失败 3-已关闭 4-待退款 5-已退款
 */
@Data
public class PayStatusVO {

    private String payNo;

    private Integer oId;

    private Integer status;
}
