package com.curry.model;

import lombok.Data;

/** 知识速记-按标签统计（非持久化） */
@Data
public class KnowledgeTagDetailStat {

    /** 标签编码 */
    private Integer tag;

    /** 题目数量（未删除的知识点） */
    private Integer count;

    /** 总耗时（秒），从 view 行为的 duration 汇总 */
    private Integer duration;

    /** 当前收藏数（status=2 且未删除） */
    private Integer favorite;

    /** 笔记数（关联的笔记条数） */
    private Integer note;

    /** 已掌握数（已删除 del_flag=1） */
    private Integer mastered;
}
