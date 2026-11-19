package com.curry.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/** 知识速记-每日刷题统计（非持久化） */
@Data
public class KnowledgeDailyStat {

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @JsonProperty("date")
    private Date date;

    /** 当日刷题数（view 次数） */
    private Integer count;

    /** 当日新增收藏数 */
    private Integer favorite;

    /** 当日新增笔记数 */
    private Integer note;

    /** 当日新增掌握数 */
    private Integer mastered;

    /** 当日刷题总耗时（秒） */
    private Integer duration;
}
