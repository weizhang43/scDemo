package com.example.scproduct.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * 商品类型统计项：按 t_product.p_type 分组计数。
 */
public class ProductTypeCountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** getPType() 默认会被 Jackson 序列化成 "ptype"，与前端约定不符，故显式指定 */
    @JsonProperty("pType")
    private Integer pType;

    private Long cnt;

    public Integer getPType() { return pType; }
    public void setPType(Integer pType) { this.pType = pType; }

    public Long getCnt() { return cnt; }
    public void setCnt(Long cnt) { this.cnt = cnt; }
}
