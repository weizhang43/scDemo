package com.example.scorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 售后工单。模块内私有实体，故留在 sc-order 不下沉 sc-model（同 ProductReview）。
 * refundAmount 是资金事实落快照；orderNo / uName 不落库，查询时联表现取。
 */
@Data
@TableName("t_after_sale")
public class AfterSale {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_REFUNDING = 1;
    public static final int STATUS_REFUNDED = 2;
    public static final int STATUS_REJECTED = 3;
    public static final int STATUS_CANCELLED = 4;

    /** 售后类型：退货退款（换货预留） */
    public static final int TYPE_REFUND = 1;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** getOId() 默认会被 Jackson 序列化成 "oid"，与前端约定不符，故显式指定（同 ProductReview） */
    @JsonProperty("oId")
    @TableField("o_id")
    private Integer oId;

    @JsonProperty("uId")
    @TableField("u_id")
    private Integer uId;

    /** 申请人展示名，不落库：联 t_user 现取 */
    @JsonProperty("uName")
    @TableField(exist = false)
    private String uName;

    /** 订单编号，不落库：联 t_order 现取（订单被删则为 null） */
    @TableField(exist = false)
    private String orderNo;

    @TableField("type")
    private Integer type;

    @TableField("reason")
    private String reason;

    /** 凭证图片，最多3张，逗号分隔的相对URL（/product/image/xxx），可为空 */
    @TableField("images")
    private String images;

    @TableField("status")
    private Integer status;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("refund_no")
    private String refundNo;

    @TableField("refund_amount")
    private BigDecimal refundAmount;

    @TableField("create_time")
    private Date createTime;

    @TableField("audit_time")
    private Date auditTime;

    @TableField("refund_time")
    private Date refundTime;

    @TableField("update_time")
    private Date updateTime;

    @TableField("version")
    private Integer version;
}
