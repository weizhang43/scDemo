package com.example.scorder.dto;

import java.io.Serializable;

/**
 * 秒杀下单请求 / 队列消息体。
 * 秒杀数量固定为 1，因此无需 quantity 字段。
 */
public class SeckillRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer uId;
    private Integer pId;
    private Integer addressId;
    private String addPerson;

    public Integer getuId() { return uId; }
    public void setuId(Integer uId) { this.uId = uId; }

    public Integer getpId() { return pId; }
    public void setpId(Integer pId) { this.pId = pId; }

    public Integer getAddressId() { return addressId; }
    public void setAddressId(Integer addressId) { this.addressId = addressId; }

    public String getAddPerson() { return addPerson; }
    public void setAddPerson(String addPerson) { this.addPerson = addPerson; }
}
