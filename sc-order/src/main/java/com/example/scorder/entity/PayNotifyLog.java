package com.example.scorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 支付回调流水：每次回调无条件落一条（主事务外插入，回滚也留痕）。
 */
@Data
@TableName("t_pay_notify_log")
public class PayNotifyLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_no")
    private String payNo;

    @TableField("transaction_id")
    private String transactionId;

    @TableField("trade_status")
    private String tradeStatus;

    @TableField("raw_params")
    private String rawParams;

    @TableField("sign_ok")
    private Integer signOk;

    @TableField("process_result")
    private String processResult;

    @TableField("create_time")
    private Date createTime;
}
