package com.curry.model;

import lombok.Data;

/** 知识点按标签聚合的统计结果，用于标签下拉框进度展示，非持久化实体 */
@Data
public class KnowledgeTagStat {
    /** 标签编码 */
    private Integer tag;
    /** 该标签下未删除的知识点总数 */
    private Integer total;
    /** 该标签下 view_count > 0 的题数（已学习） */
    private Integer learned;
}
