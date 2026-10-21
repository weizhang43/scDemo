package com.example.scproduct.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * 用户商品收藏。模块内私有实体，留在 sc-product 不下沉 sc-model（同 ProductLike）。
 * 唯一键 (u_id, p_id) 保证重复收藏幂等。
 */
@Data
@TableName("t_user_favorite")
public class UserFavorite {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @JsonProperty("uId")
    @TableField("u_id")
    private Integer uId;

    @JsonProperty("pId")
    @TableField("p_id")
    private Integer pId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("createTime")
    @TableField("create_time")
    private Date createTime;
}
