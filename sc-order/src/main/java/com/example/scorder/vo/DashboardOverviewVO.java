package com.example.scorder.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 首页工作台概览指标。商家口径为其商品明细原价快照合计（不含券抵扣），管理员口径为实付合计。
 */
public class DashboardOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private BigDecimal todayGmv;
    private Long todayOrderCount;
    private Long pendingShipCount;
    private Long pendingAfterSaleCount;
    private Long unpaidCount;

    public BigDecimal getTodayGmv() { return todayGmv; }
    public void setTodayGmv(BigDecimal todayGmv) { this.todayGmv = todayGmv; }

    public Long getTodayOrderCount() { return todayOrderCount; }
    public void setTodayOrderCount(Long todayOrderCount) { this.todayOrderCount = todayOrderCount; }

    public Long getPendingShipCount() { return pendingShipCount; }
    public void setPendingShipCount(Long pendingShipCount) { this.pendingShipCount = pendingShipCount; }

    public Long getPendingAfterSaleCount() { return pendingAfterSaleCount; }
    public void setPendingAfterSaleCount(Long pendingAfterSaleCount) {
        this.pendingAfterSaleCount = pendingAfterSaleCount;
    }

    public Long getUnpaidCount() { return unpaidCount; }
    public void setUnpaidCount(Long unpaidCount) { this.unpaidCount = unpaidCount; }
}
