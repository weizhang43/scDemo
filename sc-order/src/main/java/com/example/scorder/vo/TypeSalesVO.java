package com.example.scorder.vo;

import java.io.Serializable;

/**
 * 商品分类销量统计项：销量按一级分类分组聚合（仅统计已下单/已完成订单）。
 * categoryId/categoryName 为 NULL 表示商品未挂分类或分类已删除，前端显示「未分类」。
 */
public class TypeSalesVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer categoryId;

    private String categoryName;

    private Long salesCount;

    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public Long getSalesCount() { return salesCount; }
    public void setSalesCount(Long salesCount) { this.salesCount = salesCount; }
}
