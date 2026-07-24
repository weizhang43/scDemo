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
@TableName("t_notice")
public class Notice {
    @TableId(value = "notice_id", type = IdType.AUTO)
    @JsonProperty("noticeId")
    private Long noticeId;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @JsonProperty("coverImage")
    @TableField("cover_image")
    private String coverImage;

    @TableField("status")
    private Integer status;

    @JsonProperty("sortOrder")
    @TableField("sort_order")
    private Integer sortOrder;

    @JsonProperty("createBy")
    @TableField("create_by")
    private Integer createBy;

    @JsonProperty("createName")
    @TableField("create_name")
    private String createName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("createTime")
    @TableField("create_time")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("updateTime")
    @TableField("update_time")
    private Date updateTime;
}
