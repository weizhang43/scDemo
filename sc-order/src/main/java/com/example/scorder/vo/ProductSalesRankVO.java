package com.example.scorder.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品销量榜项。salesCount 由 t_order_item 按商品聚合得出（仅统计已下单/已完成订单），
 * price 与 imageUrl 为下游 sc-product 的当前值。
 */
public class ProductSalesRankVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** getPId() 默认会被 Jackson 序列化成 "pid"，与前端约定不符，故显式指定（同 Product.java） */
    @JsonProperty("pId")
    private Integer pId;

    @JsonProperty("pName")
    private String pName;

    private Long salesCount;
    private Integer price;
    private String imageUrl;
    /** 生效中的折扣率（1-99），无折扣时为 null */
    private Integer discount;
    /** 有效单价：有折扣时为折后价，否则等于 price */
    private BigDecimal effectivePrice;

    public Integer getPId() { return pId; }
    public void setPId(Integer pId) { this.pId = pId; }

    public String getPName() { return pName; }
    public void setPName(String pName) { this.pName = pName; }

    public Long getSalesCount() { return salesCount; }
    public void setSalesCount(Long salesCount) { this.salesCount = salesCount; }

    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Integer getDiscount() { return discount; }
    public void setDiscount(Integer discount) { this.discount = discount; }

    public BigDecimal getEffectivePrice() { return effectivePrice; }
    public void setEffectivePrice(BigDecimal effectivePrice) { this.effectivePrice = effectivePrice; }
}
