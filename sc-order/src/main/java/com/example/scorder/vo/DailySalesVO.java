package com.example.scorder.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 逐日销售趋势项。date 形如 "2026-08-06"；无销量的日期由 Service 补 0。
 */
public class DailySalesVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String date;
    private BigDecimal gmv;
    private Long orderCount;

    public DailySalesVO() {
    }

    public DailySalesVO(String date, BigDecimal gmv, Long orderCount) {
        this.date = date;
        this.gmv = gmv;
        this.orderCount = orderCount;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public BigDecimal getGmv() { return gmv; }
    public void setGmv(BigDecimal gmv) { this.gmv = gmv; }

    public Long getOrderCount() { return orderCount; }
    public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }
}
