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
@TableName("t_knowledge")
public class Knowledge {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("question")
    private String question;

    /** 答案（富文本HTML） */
    @TableField("answer")
    private String answer;

    /** 状态 1-正常 2-已收藏 */
    @TableField("status")
    private Integer status;

    /** 标签 1-Java基础与核心特性 2-Java集合与数据结构 3-Java多线程与JUC 4-JVM与性能调优 5-Spring全家桶框架 6-MyBatis与MyBatis-Plus 7-数据库与缓存 8-微服务架构 9-消息队列 10-ES搜索引擎 11-前端Vue知识 12-项目实战 */
    @TableField("tag")
    private Integer tag;

    /** 是否删除 0-否 1-是（忽略的试题逻辑删除） */
    @TableField("del_flag")
    private Integer delFlag;

    /** 查看次数，记录查看进度 */
    @TableField("view_count")
    private Integer viewCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("lastViewTime")
    @TableField("last_view_time")
    private Date lastViewTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("addTime")
    @TableField("add_time")
    private Date addTime;
}
