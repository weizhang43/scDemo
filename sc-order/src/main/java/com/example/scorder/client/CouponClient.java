package com.example.scorder.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import response.ResponseDto;

import java.math.BigDecimal;

/**
 * 优惠券服务间客户端（sc-product /product/coupon/inner/*）。
 * lock 处于 @GlobalTransactional 内，下单失败由 Seata 连带回滚券锁定；
 * use/restore 走支付回调与消息表消费线程，靠 FeignAuthHeaderConfig 的内部令牌回退鉴权。
 */
@Component
@FeignClient(value = "sc-product", contextId = "couponClient", path = "/sc-product")
public interface CouponClient {

    /**
     * 下单锁券：CAS 0→1，服务端按模板规则计算抵扣额。
     * daoResult 为带 couponAmount（抵扣额）的用户券。
     */
    @PostMapping("/product/coupon/inner/lock")
    ResponseDto<Object> lock(@RequestParam("couponId") Integer couponId,
                             @RequestParam("uId") Integer uId,
                             @RequestParam("orderAmount") BigDecimal orderAmount);

    /** 支付成功核销：CAS 1→2 绑定订单，重复核销同一订单幂等成功 */
    @PostMapping("/product/coupon/inner/use")
    ResponseDto<Object> use(@RequestParam("couponId") Integer couponId,
                            @RequestParam("oId") Integer oId);

    /** 取消/退款返还：1|2→0，天然幂等 */
    @PostMapping("/product/coupon/inner/restore")
    ResponseDto<Object> restore(@RequestParam("couponId") Integer couponId);
}
