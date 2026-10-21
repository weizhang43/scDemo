package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * 会员积分流水。获取与消耗都落一行，余额/累计由 SUM 聚合。
 * uk(u_id, order_no, type) 保证支付回调重试的加分幂等。
 */
@Data
@TableName("t_points_record")
public class PointsRecord {

    /** 类型：支付成功获取 */
    public static final String TYPE_EARN = "EARN";
    /** 类型：兑换优惠券消耗 */
    public static final String TYPE_REDEEM = "REDEEM";
    /** 类型：兑换失败补偿返还 */
    public static final String TYPE_COMPENSATE = "COMPENSATE";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @JsonProperty("uId")
    @TableField("u_id")
    private Integer uId;

    @JsonProperty("changeValue")
    @TableField("change_value")
    private Integer changeValue;

    @TableField("type")
    private String type;

    @JsonProperty("orderNo")
    @TableField("order_no")
    private String orderNo;

    @TableField("remark")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("createTime")
    @TableField("create_time")
    private Date createTime;
}
