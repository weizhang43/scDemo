package com.example.scorder.dto;

import java.io.Serializable;

/**
 * 秒杀下单请求 / 队列消息体。
 * 秒杀数量固定为 1，因此无需 quantity 字段。
 * 价格由 activityId 在服务端查出，不接受调用方传价。
 */
public class SeckillRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer uId;
    /** 秒杀活动ID：商品、名额、时间窗与秒杀价都由它决定 */
    private Integer activityId;
    private Integer addressId;
    private String addPerson;

    public Integer getuId() { return uId; }
    public void setuId(Integer uId) { this.uId = uId; }

    public Integer getActivityId() { return activityId; }
    public void setActivityId(Integer activityId) { this.activityId = activityId; }

    public Integer getAddressId() { return addressId; }
    public void setAddressId(Integer addressId) { this.addressId = addressId; }

    public String getAddPerson() { return addPerson; }
    public void setAddPerson(String addPerson) { this.addPerson = addPerson; }
}
