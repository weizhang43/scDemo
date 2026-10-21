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
 * 用户浏览足迹。每用户每商品一条（唯一键），重复浏览只刷新时间。
 * category_id 为浏览时快照，供推荐加权使用，商品改分类不影响历史足迹。
 */
@Data
@TableName("t_browse_history")
public class BrowseHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @JsonProperty("uId")
    @TableField("u_id")
    private Integer uId;

    @JsonProperty("pId")
    @TableField("p_id")
    private Integer pId;

    @JsonProperty("categoryId")
    @TableField("category_id")
    private Integer categoryId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("createTime")
    @TableField("create_time")
    private Date createTime;
}
