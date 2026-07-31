package com.curry.model;

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
 * 商品折扣活动。是否生效完全由时间窗决定，取消活动即删除该行。
 */
@Data
@TableName("t_product_promotion")
public class ProductPromotion {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @JsonProperty("pId")
    @TableField("p_id")
    private Integer pId;

    /** 折扣率 1-99，如 85 表示 8.5 折 */
    @TableField("discount")
    private Integer discount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("startTime")
    @TableField("start_time")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("endTime")
    @TableField("end_time")
    private Date endTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("createTime")
    @TableField("create_time")
    private Date createTime;

    /** 商品名，列表展示用，非持久化字段 */
    @JsonProperty("pName")
    @TableField(exist = false)
    private String pName;

    /** 商品原价，列表展示用，非持久化字段 */
    @TableField(exist = false)
    private Integer price;

    /** 折后价，列表展示用，非持久化字段 */
    @JsonProperty("effectivePrice")
    @TableField(exist = false)
    private BigDecimal effectivePrice;
}
