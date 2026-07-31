package com.example.scorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * 购物车行。模块内私有实体，故留在 sc-order 不下沉 sc-model。
 * 只存 (u_id, p_id, quantity)：价格与商品名不做快照，列表接口每次向 sc-product 回源，
 * 否则快照价拿去当 expectedPrice 必然撞 doPlaceOrder 的「价格已更新」。
 */
@Data
@TableName("t_cart_item")
public class CartItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** getUId() 默认会被 Jackson 序列化成 "uid"，与前端约定不符，故显式指定（同 Product.java） */
    @JsonProperty("uId")
    @TableField("u_id")
    private Integer uId;

    @JsonProperty("pId")
    @TableField("p_id")
    private Integer pId;

    @TableField("quantity")
    private Integer quantity;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
