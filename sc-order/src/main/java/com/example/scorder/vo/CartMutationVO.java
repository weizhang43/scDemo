package com.example.scorder.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * 加购 / 改数量的返回体。带上 cartCount 让前端一次请求就能刷新导航角标，
 * 不必再发一次 /order/cart/count。
 */
public class CartMutationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** getPId() 默认会被 Jackson 序列化成 "pid"，与前端约定不符，故显式指定（同 Product.java） */
    @JsonProperty("pId")
    private Integer pId;

    /** 实际落库数量（可能被库存上限截断，服务端对最终数字有最终决定权） */
    private Integer quantity;

    /** 是否被上限截断：为 true 时 quantity 即该商品当前可购上限 */
    private Boolean capped;

    /** 该用户购物车条目数（商品种类数） */
    private Integer cartCount;

    public Integer getPId() { return pId; }
    public void setPId(Integer pId) { this.pId = pId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Boolean getCapped() { return capped; }
    public void setCapped(Boolean capped) { this.capped = capped; }

    public Integer getCartCount() { return cartCount; }
    public void setCartCount(Integer cartCount) { this.cartCount = cartCount; }
}
