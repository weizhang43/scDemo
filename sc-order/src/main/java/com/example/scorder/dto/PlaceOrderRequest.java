package com.example.scorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 批量下单请求
 */
@Data
public class PlaceOrderRequest {
    @JsonProperty("uId")
    private Integer uId;
    private String addPerson;
    private Integer addressId;
    private Integer orderStatus;
    private List<Item> items;

    @Data
    public static class Item {
        @JsonProperty("pId")
        private Integer pId;
        private Integer quantity;
        /**
         * 下单时页面上展示的单价（元），仅用于比对，不参与金额计算。
         * 与服务端权威有效价不一致说明促销恰在下单瞬间开始/结束，直接拒单让用户看到新价格。
         */
        @JsonProperty("expectedPrice")
        private BigDecimal expectedPrice;
    }
}
