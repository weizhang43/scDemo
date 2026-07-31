package com.example.scorder.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车行视图。除 id / quantity 外全部是每次请求向 sc-product 回源的实时值，
 * 库里不存快照，effectivePrice 直接给前端当下单的 expectedPrice 用。
 * available 与 exceedStock 故意分开：前者用户改不了（只能删），后者调数量就能修好，
 * 两者都禁止勾选，但前端渲染不同。
 */
public class CartItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 购物车行主键，前端 el-table 的 row-key */
    private Integer id;

    /** getPId() 默认会被 Jackson 序列化成 "pid"，与前端约定不符，故显式指定（同 Product.java） */
    @JsonProperty("pId")
    private Integer pId;

    /** 实时商品名；商品被硬删时为 null，前端退化显示「商品 #pId」 */
    @JsonProperty("pName")
    private String pName;

    private String imageUrl;

    /** 加购数量（库里的值） */
    private Integer quantity;

    /** 实时原价（整数元） */
    private Integer price;

    /** 生效中的折扣率（1-99），无折扣时为 null */
    private Integer discount;

    /** 实时有效单价：有折扣时为折后价，否则等于 price。前端拿它当 expectedPrice */
    private BigDecimal effectivePrice;

    /** 实时库存；不可售时置 0 */
    private Integer stock;

    /** 可售且有库存 —— 前端 :selectable 的依据 */
    private Boolean available;

    /** available 但加购数量已超过实时库存，调小数量即可恢复 */
    private Boolean exceedStock;

    /** 不可购买的中文原因，正常时为 null */
    private String unavailableReason;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getPId() { return pId; }
    public void setPId(Integer pId) { this.pId = pId; }

    public String getPName() { return pName; }
    public void setPName(String pName) { this.pName = pName; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }

    public Integer getDiscount() { return discount; }
    public void setDiscount(Integer discount) { this.discount = discount; }

    public BigDecimal getEffectivePrice() { return effectivePrice; }
    public void setEffectivePrice(BigDecimal effectivePrice) { this.effectivePrice = effectivePrice; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }

    public Boolean getExceedStock() { return exceedStock; }
    public void setExceedStock(Boolean exceedStock) { this.exceedStock = exceedStock; }

    public String getUnavailableReason() { return unavailableReason; }
    public void setUnavailableReason(String unavailableReason) { this.unavailableReason = unavailableReason; }
}
