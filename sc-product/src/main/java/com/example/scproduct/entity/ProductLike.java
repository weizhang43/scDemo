package com.example.scproduct.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * 商品点赞记录。模块内私有实体，故留在 sc-product 不下沉 sc-model。
 * 只记录「谁点过什么」用于去重，点赞总数仍由 Redis 计数器 + xxl-job 回写 t_product.like_count 承担。
 */
@Data
@TableName("t_product_like")
public class ProductLike {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** getUId() 默认会被 Jackson 序列化成 "uid"，与前端约定不符，故显式指定（同 Product.java） */
    @JsonProperty("uId")
    @TableField("u_id")
    private Integer uId;

    @JsonProperty("pId")
    @TableField("p_id")
    private Integer pId;

    @TableField("create_time")
    private Date createTime;
}
