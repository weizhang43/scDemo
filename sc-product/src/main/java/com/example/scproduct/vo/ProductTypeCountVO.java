package com.example.scproduct.vo;

import java.io.Serializable;

/**
 * 商品分类统计项：按一级分类分组计数（二级分类归并到其根分类）。
 * categoryId/categoryName 为 NULL 表示商品未挂分类或分类已删除，前端显示「未分类」。
 */
public class ProductTypeCountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer categoryId;

    private String categoryName;

    private Long cnt;

    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public Long getCnt() { return cnt; }
    public void setCnt(Long cnt) { this.cnt = cnt; }
}
