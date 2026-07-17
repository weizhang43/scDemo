package com.example.scproduct.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.DateFormat;

import java.util.Date;

/**
 * 商品描述 ES 文档：仅承载 proDesc 模糊检索所需的字段，避免整体实体同步带来的双写一致性开销。
 * 索引按 pId 建立主键，多次 upsert 幂等。
 */
@Data
@Document(indexName = "product_desc", createIndex = true)
public class ProductDescDoc {

    @Id
    @Field(type = FieldType.Long)
    private Long pId;

    /** 冗余轻量字段，便于在 ES 内联过滤；权威值仍以 MySQL 为准 */
    @Field(type = FieldType.Keyword)
    private String pName;

    /** 大字段：中文分词匹配，proDesc 模糊检索的核心 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String proDesc;

    @Field(type = FieldType.Integer)
    private Integer isExpired;

    @Field(type = FieldType.Integer)
    private Integer stock;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Date updateTime;
}
