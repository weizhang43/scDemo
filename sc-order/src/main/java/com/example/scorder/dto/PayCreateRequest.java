package com.example.scorder.dto;

import lombok.Data;

@Data
public class PayCreateRequest {

    private Integer oId;

    /** 支付渠道，当前仅 MOCK */
    private String channel;
}
