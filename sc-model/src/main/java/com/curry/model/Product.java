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

@Data
@TableName("t_product")
public class Product {
    @TableId(value = "p_id", type = IdType.AUTO)
    @JsonProperty("pId")
    private Integer pId;

    @JsonProperty("pName")
    @TableField("p_name")
    private String pName;

    @TableField("price")
    private Integer price;

    @TableField("stock")
    private Integer stock;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @JsonProperty("productionDate")
    @TableField("production_date")
    private Date productionDate;

    @JsonProperty("shelfLife")
    @TableField("shelf_life")
    private Integer shelfLife;

    @TableField("origin")
    private String origin;

    @JsonProperty("isExpired")
    @TableField("is_expired")
    private Integer isExpired;

    @TableField("manufacturer")
    private String manufacturer;

    @TableField("pro_desc")
    private String proDesc;


    @JsonProperty("likeCount")
    @TableField("like_count")
    private Integer likeCount;

    @JsonProperty("imageUrl")
    @TableField("image_url")
    private String imageUrl;

    /** 所属商家用户ID（t_user.u_id，u_type=1）；NULL 视为公共商品，任何商家可管理 */
    @JsonProperty("merchantId")
    @TableField("merchant_id")
    private Integer merchantId;

    /** 上架状态 1-上架 0-下架；下架后顾客端不可见 */
    @TableField("status")
    private Integer status;

    /**
     * 生效中的折扣率（1-99，如 85 表示 8.5 折），无生效折扣时为 null。
     * 非持久化字段，由折扣活动查询后回填。
     */
    @TableField(exist = false)
    private Integer discount;

    /**
     * 有效单价：有生效折扣时为折后价，否则等于 price。
     * 用 BigDecimal 是因为 price 为整数元，33 元打 8.5 折 = 28.05，Integer 装不下。
     * 非持久化字段，由折扣活动查询后回填。
     */
    @TableField(exist = false)
    private BigDecimal effectivePrice;

    public Product(){

    }

    public Product(Integer pId,Integer stock){
        this.pId = pId;
        this.stock = stock;
    }
}
