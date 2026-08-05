package com.example.scorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 支付单。status: 0-待支付 1-成功 2-失败 3-已关闭 4-待退款 5-已退款
 */
@Data
@TableName("t_pay_record")
public class PayRecord {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAIL = 2;
    public static final int STATUS_CLOSED = 3;
    public static final int STATUS_REFUNDING = 4;
    public static final int STATUS_REFUNDED = 5;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_no")
    private String payNo;

    @TableField("o_id")
    private Integer oId;

    @TableField("order_no")
    private String orderNo;

    @TableField("u_id")
    private Integer uId;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("channel")
    private String channel;

    @TableField("status")
    private Integer status;

    @TableField("transaction_id")
    private String transactionId;

    @TableField("pay_time")
    private Date payTime;

    @TableField("notify_time")
    private Date notifyTime;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;

    @TableField("version")
    private Integer version;
}
