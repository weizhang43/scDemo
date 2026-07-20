package com.example.scorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_order_stock_restore_msg")
public class OrderStockRestoreMsg {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("o_id")
    private Integer oId;

    @TableField("status")
    private Integer status;

    @TableField("retry_cnt")
    private Integer retryCnt;

    @TableField("max_retry")
    private Integer maxRetry;

    @TableField("next_retry")
    private Date nextRetry;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
