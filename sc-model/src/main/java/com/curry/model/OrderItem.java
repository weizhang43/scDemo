package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("t_order_item")
public class OrderItem {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @JsonProperty("oId")
    @TableField("o_id")
    private Integer oId;

    @JsonProperty("pId")
    @TableField("p_id")
    private Integer pId;

    @TableField("p_name")
    private String pName;

    @TableField("price")
    private BigDecimal price;

    @TableField("quantity")
    private Integer quantity;

    @TableField(exist = false)
    private BigDecimal subtotal;
}
