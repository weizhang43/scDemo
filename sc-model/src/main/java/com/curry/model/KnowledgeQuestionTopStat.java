package com.curry.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** 知识速记-题目维度 TOP10 排行结果（非持久化） */
@Data
public class KnowledgeQuestionTopStat {

    /** 笔记次数 TOP10 */
    @JsonProperty("noteTop")
    private List<KnowledgeQuestionTopItem> noteTop;

    /** 耗时 TOP10（分钟） */
    @JsonProperty("durationTop")
    private List<KnowledgeQuestionTopItem> durationTop;

    /** 查看次数 TOP10 */
    @JsonProperty("viewTop")
    private List<KnowledgeQuestionTopItem> viewTop;
}
