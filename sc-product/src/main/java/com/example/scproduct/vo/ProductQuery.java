package com.example.scproduct.vo;

import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 商品列表查询条件：分页查询与导出共用。
 * 字段名与前端请求参数名一一对应（对象绑定），新增/导出接口的 HTTP 参数保持不变。
 */
public class ProductQuery {

    /** 商品名称，模糊匹配 */
    private String pName;
    /** 商品描述，ES 模糊检索（不可用时降级 MySQL LIKE） */
    private String proDesc;
    /** 生产日期区间起 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date productionDateStart;
    /** 生产日期区间止 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date productionDateEnd;
    /** 产地，精确匹配 */
    private String origin;
    /** 是否过期：1-已过期 0-未过期，null 不限 */
    private Integer isExpired;
    /** 上下架状态：1-在售 0-已下架，null 不限（仅分页查询使用） */
    private Integer status;
    /** 分类过滤；一级分类自动展开为「自身 + 子分类」，null 不限（仅分页查询使用） */
    private Integer categoryId;
    /** 排序：sales-成交数 reviews-评价数 likes-点赞数，其余（含 null）按 id 倒序 */
    private String sortBy;
    /** 页码，默认 1 */
    private int pageNo = 1;
    /** 每页条数，默认 10 */
    private int pageSize = 10;

    public String getpName() {
        return pName;
    }

    public void setpName(String pName) {
        this.pName = pName;
    }

    public String getProDesc() {
        return proDesc;
    }

    public void setProDesc(String proDesc) {
        this.proDesc = proDesc;
    }

    public Date getProductionDateStart() {
        return productionDateStart;
    }

    public void setProductionDateStart(Date productionDateStart) {
        this.productionDateStart = productionDateStart;
    }

    public Date getProductionDateEnd() {
        return productionDateEnd;
    }

    public void setProductionDateEnd(Date productionDateEnd) {
        this.productionDateEnd = productionDateEnd;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public Integer getIsExpired() {
        return isExpired;
    }

    public void setIsExpired(Integer isExpired) {
        this.isExpired = isExpired;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
