package com.example.scorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AfterSaleApplyRequest {

    @JsonProperty("oId")
    private Integer oId;

    /** 售后类型，本期仅 1:退货退款（换货预留），缺省按 1 处理 */
    private Integer type;

    private String reason;

    /** 凭证图片相对URL，最多3张，可为空 */
    private List<String> images;
}
