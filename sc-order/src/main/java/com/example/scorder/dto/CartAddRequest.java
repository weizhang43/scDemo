package com.example.scorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 加购与改数量共用的请求体。
 * 不含 uId —— 身份只从网关注入的 X-User-Id 取，不采信前端。
 */
@Data
public class CartAddRequest {

    @JsonProperty("pId")
    private Integer pId;

    /** 加购时为增量（空或小于 1 按 1 处理），改数量时为目标值 */
    private Integer quantity;
}
