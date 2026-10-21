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
@TableName("t_message")
public class Message {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("u_id")
    private Integer uId;

    @JsonProperty("msgType")
    @TableField("msg_type")
    private String msgType;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @JsonProperty("bizId")
    @TableField("biz_id")
    private String bizId;

    @JsonProperty("isRead")
    @TableField("is_read")
    private Integer isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("createTime")
    @TableField("create_time")
    private Date createTime;
}
