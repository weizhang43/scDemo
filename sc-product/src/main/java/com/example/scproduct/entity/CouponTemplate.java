package com.example.scproduct.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 优惠券模板。营销域私有实体，留在 sc-product 不下沉 sc-model（sc-order 只经 Feign 使用）。
 */
@Data
@TableName("t_coupon_template")
public class CouponTemplate {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 发行商家ID，0=平台通用（仅管理员可发） */
    @TableField("merchant_id")
    private Integer merchantId;

    @TableField("name")
    private String name;

    /** 券类型 1:满减 2:折扣 */
    @TableField("type")
    private Integer type;

    /** 使用门槛：订单应付满 X 元可用，0 不限 */
    @TableField("threshold_amount")
    private BigDecimal thresholdAmount;

    /** 满减券减免金额（type=1 必填） */
    @TableField("off_amount")
    private BigDecimal offAmount;

    /** 折扣率 0-1，如 0.85 为 85 折（type=2 必填） */
    @TableField("discount_rate")
    private BigDecimal discountRate;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("remain_count")
    private Integer remainCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("valid_start")
    private Date validStart;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("valid_end")
    private Date validEnd;

    /** 1-有效 0-已停用 */
    @TableField("status")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("create_time")
    private Date createTime;

    /** Redis 实时剩余量，未播种时取 remainCount */
    @TableField(exist = false)
    private Integer redisRemain;

    /** 领券中心：当前用户是否已领 */
    @TableField(exist = false)
    private Boolean claimed;
}
