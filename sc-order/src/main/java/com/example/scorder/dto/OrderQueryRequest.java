package com.example.scorder.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 订单分页查询条件对象（替代原 8 个散列参数）。
 * 字段名与原 @RequestParam 参数名完全一致，Spring 按字段名绑定查询串，前端无需改动。
 */
public class OrderQueryRequest {

    /** 默认页码 */
    private static final int DEFAULT_PAGE_NO = 1;
    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 关键字（按下单人模糊匹配） */
    private String key;

    /** 订单号（模糊匹配） */
    private String orderNo;

    /** 订单状态：-1 取消 / 0 待付款 / 1 已支付 / 2 已完成 / 3 已发货 */
    private Integer orderStatus;

    /** 创建时间起（yyyy-MM-dd） */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date createTimeStart;

    /** 创建时间止（yyyy-MM-dd） */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date createTimeEnd;

    /** 页码，从 1 开始 */
    private int pageNo = DEFAULT_PAGE_NO;

    /** 每页条数 */
    private int pageSize = DEFAULT_PAGE_SIZE;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }

    public Date getCreateTimeStart() {
        return createTimeStart == null ? null : new Date(createTimeStart.getTime());
    }

    public void setCreateTimeStart(Date createTimeStart) {
        this.createTimeStart = createTimeStart == null ? null : new Date(createTimeStart.getTime());
    }

    public Date getCreateTimeEnd() {
        return createTimeEnd == null ? null : new Date(createTimeEnd.getTime());
    }

    public void setCreateTimeEnd(Date createTimeEnd) {
        this.createTimeEnd = createTimeEnd == null ? null : new Date(createTimeEnd.getTime());
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
