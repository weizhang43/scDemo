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
     * 商品类型 1-食品饮品 2-电子产品 3-服装饰品 4-家用电器 5-汽车 6-厨房用品 7-其他
     * @deprecated 已由 categoryId（t_category 两级分类）取代，列保留仅为兼容回滚
     */
    @Deprecated
    @JsonProperty("pType")
    @TableField("p_type")
    private Integer pType;

    /** 分类ID（t_category.id），可挂一级或二级分类 */
    @JsonProperty("categoryId")
    @TableField("category_id")
    private Integer categoryId;

    /** 分类名称。非持久化字段，联 t_category 现取。 */
    @JsonProperty("categoryName")
    @TableField(exist = false)
    private String categoryName;

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

    /**
     * 成交数：order_status IN (1,2) 的订单明细 quantity 之和，口径同首页销量榜。
     * 非持久化字段，由 selectPageWithStats 联表现取。
     */
    @TableField(exist = false)
    private Integer saleCount;

    /** 评价数。非持久化字段，由 selectPageWithStats 联表现取。 */
    @TableField(exist = false)
    private Integer reviewCount;

    public Product(){

    }

    public Product(Integer pId,Integer stock){
        this.pId = pId;
        this.stock = stock;
    }
}
