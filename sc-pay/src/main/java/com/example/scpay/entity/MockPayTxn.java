package com.example.scpay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 模拟网关交易单。status: 0-待支付 1-成功 2-失败 3-已关闭
 */
@Data
@TableName("t_mock_pay_txn")
public class MockPayTxn {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAIL = 2;
    public static final int STATUS_CLOSED = 3;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("transaction_id")
    private String transactionId;

    @TableField("pay_no")
    private String payNo;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("subject")
    private String subject;

    @TableField("status")
    private Integer status;

    @TableField("notify_url")
    private String notifyUrl;

    @TableField("notify_cnt")
    private Integer notifyCnt;

    @TableField("last_notify_result")
    private String lastNotifyResult;

    @TableField("refund_time")
    private Date refundTime;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
