package com.example.scproduct.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户优惠券。展示字段联表现取（模板名/规则/有效期），不留冗余快照；
 * coupon_amount 是锁定时算出的抵扣额快照，金额事实以 t_order.coupon_amount 为准。
 */
@Data
@TableName("t_user_coupon")
public class UserCoupon {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("template_id")
    private Integer templateId;

    /** getUId() 默认被 Jackson 序列化成 "uid"，与前端约定不符，显式指定（同 ProductLike） */
    @JsonProperty("uId")
    @TableField("u_id")
    private Integer uId;

    /** 0-未使用 1-已锁定(下单占用) 2-已使用 */
    @TableField("status")
    private Integer status;

    /** 核销订单ID（use 时绑定，仅留痕） */
    @JsonProperty("oId")
    @TableField("o_id")
    private Integer oId;

    /** 锁定时按订单额算出的抵扣金额快照 */
    @TableField("coupon_amount")
    private BigDecimal couponAmount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("claim_time")
    private Date claimTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("use_time")
    private Date useTime;

    // ------ 联表展示字段 ------

    @TableField(exist = false)
    private String name;

    @TableField(exist = false)
    private Integer type;

    @TableField(exist = false)
    private BigDecimal thresholdAmount;

    @TableField(exist = false)
    private BigDecimal offAmount;

    @TableField(exist = false)
    private BigDecimal discountRate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(exist = false)
    private Date validStart;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(exist = false)
    private Date validEnd;
}
