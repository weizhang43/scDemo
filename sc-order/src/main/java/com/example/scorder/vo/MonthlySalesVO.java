package com.example.scorder.vo;

import java.io.Serializable;

/**
 * 月度销量统计项。month 形如 "2026-08"；无销量的月份由 Service 补 0。
 */
public class MonthlySalesVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String month;
    private Long salesCount;

    public MonthlySalesVO() {
    }

    public MonthlySalesVO(String month, Long salesCount) {
        this.month = month;
        this.salesCount = salesCount;
    }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    public Long getSalesCount() { return salesCount; }
    public void setSalesCount(Long salesCount) { this.salesCount = salesCount; }
}
