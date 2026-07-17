package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

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
}
