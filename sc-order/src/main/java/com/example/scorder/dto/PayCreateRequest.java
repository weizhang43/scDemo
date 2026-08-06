package com.example.scorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PayCreateRequest {

    // Lombok 生成 getOId()，Jackson 默认会把属性名折算成 oid，显式声明与前端的 oId 对齐
    @JsonProperty("oId")
    private Integer oId;

    /** 支付渠道，当前仅 MOCK */
    private String channel;
}
