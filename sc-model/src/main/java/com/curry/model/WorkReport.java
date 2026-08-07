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
@TableName("t_work_report")
public class WorkReport {
    @TableId(value = "report_id", type = IdType.AUTO)
    @JsonProperty("reportId")
    private Long reportId;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    /** 类型 1-日报 2-周报 */
    @TableField("type")
    private Integer type;

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
