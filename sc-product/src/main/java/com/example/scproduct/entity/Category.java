package com.example.scproduct.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 商品分类（两级树）。parent_id=0 为一级分类，二级分类挂在一级之下，不允许更深层级。
 * 初始 id 1-7 对齐已废弃的 t_product.p_type 字典，新分类 id 从 100 起步。
 */
@Data
@TableName("t_category")
public class Category {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("parent_id")
    private Integer parentId;

    @TableField("name")
    private String name;

    @TableField("sort")
    private Integer sort;

    /** 状态 1-启用 0-停用 */
    @TableField("status")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("create_time")
    private Date createTime;

    @TableField(exist = false)
    private List<Category> children;
}
