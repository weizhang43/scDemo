package com.example.scorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 发表评价的请求体。
 * 不含 uId —— 身份只从网关注入的 X-User-Id 取，不采信前端（同 CartAddRequest）。
 */
@Data
public class ReviewSubmitRequest {

    @JsonProperty("oId")
    private Integer oId;

    @JsonProperty("pId")
    private Integer pId;

    /** 星级 1-5 */
    private Integer rating;

    /** 文字评论，可为空 —— 只打星不写字也算一次有效评价 */
    private String content;
}
