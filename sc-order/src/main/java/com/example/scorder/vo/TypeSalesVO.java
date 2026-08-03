package com.example.scorder.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * 商品类型销量统计项：销量按 t_product.p_type 分组聚合（仅统计已下单/已完成订单）。
 */
public class TypeSalesVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** getPType() 默认会被 Jackson 序列化成 "ptype"，与前端约定不符，故显式指定 */
    @JsonProperty("pType")
    private Integer pType;

    private Long salesCount;

    public Integer getPType() { return pType; }
    public void setPType(Integer pType) { this.pType = pType; }

    public Long getSalesCount() { return salesCount; }
    public void setSalesCount(Long salesCount) { this.salesCount = salesCount; }
}
