package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@TableName("t_order")
public class Order {
    @TableId(value = "o_id", type = IdType.AUTO)
    private Integer oId;

    @TableField("order_no")
    private String orderNo;

    @TableField("add_person")
    private String addPerson;

    /** 下单顾客ID。历史订单回填不到时为 null，顾客一律查不到 */
    @TableField("u_id")
    private Integer uId;

    @TableField("create_time")
    private Date createTime;

    @TableField("order_address")
    private String orderAddress;

    @TableField("order_amount")
    private BigDecimal orderAmount;

    @TableField("order_status")
    private Integer orderStatus;

    @Version
    @TableField("version")
    private Integer version;

    @TableField(exist = false)
    private List<Product> productList;

    @TableField(exist = false)
    private List<OrderItem> orderItems;
}
