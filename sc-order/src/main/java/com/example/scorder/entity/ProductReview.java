package com.example.scorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * 商品评价。模块内私有实体，故留在 sc-order 不下沉 sc-model。
 * pName 是落库快照；uName 不落库，查询时按 u_id 联 t_user 现取。
 */
@Data
@TableName("t_product_review")
public class ProductReview {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** getUId() 默认会被 Jackson 序列化成 "uid"，与前端约定不符，故显式指定（同 CartItem.java） */
    @JsonProperty("uId")
    @TableField("u_id")
    private Integer uId;

    /** 评价人展示名，不落库：联表现取，用户改名后评价区跟着变 */
    @JsonProperty("uName")
    @TableField(exist = false)
    private String uName;

    /** 评价人头像，不落库：同 uName 联表现取 */
    @JsonProperty("uAvatar")
    @TableField(exist = false)
    private String uAvatar;

    @JsonProperty("oId")
    @TableField("o_id")
    private Integer oId;

    @JsonProperty("pId")
    @TableField("p_id")
    private Integer pId;

    @JsonProperty("pName")
    @TableField("p_name")
    private String pName;

    /** 星级 1-5 */
    @TableField("rating")
    private Integer rating;

    @TableField("content")
    private String content;

    @TableField("create_time")
    private Date createTime;
}
