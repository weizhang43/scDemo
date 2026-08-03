package com.example.scorder.vo;

import com.example.scorder.entity.ProductReview;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商品评价概览：一次请求同时拿到平均分、总条数与当页评论，
 * 商品详情页无需为「平均分」再发一次请求。
 */
public class ProductReviewSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 平均星级，保留一位小数；无评价时为 0 */
    private BigDecimal avgRating;

    /** 评价总条数（不是当页条数） */
    private Long total;

    /** 当页评论，按时间倒序 */
    private List<ProductReview> records;

    public BigDecimal getAvgRating() { return avgRating; }
    public void setAvgRating(BigDecimal avgRating) { this.avgRating = avgRating; }

    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }

    public List<ProductReview> getRecords() { return records; }
    public void setRecords(List<ProductReview> records) { this.records = records; }
}
