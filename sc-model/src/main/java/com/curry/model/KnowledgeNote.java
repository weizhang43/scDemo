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
@TableName("t_knowledge_note")
public class KnowledgeNote {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @JsonProperty("knowledgeId")
    @TableField("knowledge_id")
    private Long knowledgeId;

    /** 笔记内容（纯文本） */
    @TableField("content")
    private String content;

    /** 是否重点 0-否 1-是 */
    @TableField("important")
    private Integer important;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("createTime")
    @TableField("create_time")
    private Date createTime;
}
