package com.example.scorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

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
        private Integer price;
    }
}
