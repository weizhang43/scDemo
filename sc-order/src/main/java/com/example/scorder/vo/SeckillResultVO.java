package com.example.scorder.vo;

import java.io.Serializable;

/**
 * 秒杀结果。
 * status:
 *   PENDING  - 预扣成功，订单异步处理中（前端需轮询）
 *   SUCCESS  - 下单成功（orderNo 有值）
 *   FAILED   - 预扣失败或落库失败（msg 为原因），非 PENDING 均为终态
 */
public class SeckillResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String status;
    private String msg;
    private String orderNo;

    public SeckillResultVO() {
    }

    public SeckillResultVO(String status, String msg, String orderNo) {
        this.status = status;
        this.msg = msg;
        this.orderNo = orderNo;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
}
