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
@TableName("t_study_plan")
public class StudyPlan {
    @TableId(value = "plan_id", type = IdType.AUTO)
    @JsonProperty("planId")
    private Long planId;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @JsonProperty("publishDate")
    @TableField("publish_date")
    private Date publishDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @JsonProperty("planDate")
    @TableField("plan_date")
    private Date planDate;

    @JsonProperty("publishName")
    @TableField("publish_name")
    private String publishName;

    /** 状态 1-已发布 2-已完成 3-已超期 */
    @TableField("status")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @JsonProperty("finishDate")
    @TableField("finish_date")
    private Date finishDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("createTime")
    @TableField("create_time")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("updateTime")
    @TableField("update_time")
    private Date updateTime;
}
