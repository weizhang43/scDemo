package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * 知识速记-用户行为记录表：每次查看/收藏/取消收藏/添加笔记/掌握都会插入一条，用于刷题统计
 */
@Data
@TableName("t_knowledge_action")
public class KnowledgeAction {

    /** 行为类型：查看 */
    public static final String ACTION_VIEW = "view";
    /** 行为类型：收藏 */
    public static final String ACTION_FAVORITE = "favorite";
    /** 行为类型：取消收藏 */
    public static final String ACTION_UNFAVORITE = "unfavorite";
    /** 行为类型：添加笔记 */
    public static final String ACTION_NOTE = "note";
    /** 行为类型：标记掌握（忽略） */
    public static final String ACTION_MASTER = "master";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @JsonProperty("knowledgeId")
    @TableField("knowledge_id")
    private Long knowledgeId;

    /** 行为类型 view/favorite/unfavorite/note/master */
    @TableField("action")
    private String action;

    /** 标签冗余字段，便于按标签聚合时无需 join */
    @TableField("tag")
    private Integer tag;

    /** 刷题耗时（秒），仅 view 行为可能 > 0 */
    @TableField("duration")
    private Integer duration;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("createTime")
    @TableField("create_time")
    private Date createTime;
}
