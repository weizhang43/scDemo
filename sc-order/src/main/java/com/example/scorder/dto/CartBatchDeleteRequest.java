package com.example.scorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 批量删除购物车商品。以商品ID为键而非购物车行ID：
 * (u_id, p_id) 唯一所以不含糊，且 pIds 正是前端拼完 placeOrderV2 后手里已有的东西，
 * 结算清车不用额外记行ID。
 * 不含 uId —— 身份只从网关注入的 X-User-Id 取。
 */
@Data
public class CartBatchDeleteRequest {

    @JsonProperty("pIds")
    private List<Integer> pIds;
}
