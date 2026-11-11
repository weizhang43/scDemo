package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 区域表：省市区三级结构，parentId=0 为省级。
 */
@Data
@TableName("region")
public class Region {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("name")
    private String name;
    @TableField("parent_id")
    private Long parentId;
    @TableField("level")
    private Integer level;
    @TableField("sort_order")
    private Integer sortOrder;
}