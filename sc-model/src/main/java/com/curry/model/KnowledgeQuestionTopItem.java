package com.curry.model;

import lombok.Data;

/** 知识速记-单题排行条目（非持久化） */
@Data
public class KnowledgeQuestionTopItem {

    /** 题目 ID */
    private Long id;

    /** 题干（富文本，前端可截断展示） */
    private String question;

    /** 排行的数值（笔记数/查看次数/耗时秒数，由调用方决定含义） */
    private Integer value;
}
