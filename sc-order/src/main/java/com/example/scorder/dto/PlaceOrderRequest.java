package com.example.scorder.dto;

import java.util.List;

/**
 * 批量下单请求
 */
public class PlaceOrderRequest {
    private Integer uId;
    private String addPerson;
    private Integer addressId;
    private List<Item> items;

    public static class Item {
        private Integer pId;
        private Integer quantity;
        private Integer price;

        public Integer getpId() { return pId; }
        public void setpId(Integer pId) { this.pId = pId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Integer getPrice() { return price; }
        public void setPrice(Integer price) { this.price = price; }
    }

    public Integer getuId() { return uId; }
    public void setuId(Integer uId) { this.uId = uId; }
    public String getAddPerson() { return addPerson; }
    public void setAddPerson(String addPerson) { this.addPerson = addPerson; }
    public Integer getAddressId() { return addressId; }
    public void setAddressId(Integer addressId) { this.addressId = addressId; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
}
