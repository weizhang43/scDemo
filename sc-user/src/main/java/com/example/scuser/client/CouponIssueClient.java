package com.example.scuser.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import response.ResponseDto;

import java.math.BigDecimal;

/**
 * 优惠券发放服务间客户端（sc-product /product/coupon/inner/issue）。
 * 积分兑换券走独立隐藏模板 + 直发用户券，与领券中心的限量模板互不影响。
 */
@Component
@FeignClient(value = "sc-product", contextId = "couponIssueClient", path = "/sc-product")
public interface CouponIssueClient {

    /**
     * 发放一张积分兑换券：创建隐藏模板并直接写入用户券。
     * @return daoResult 为新用户券
     */
    @PostMapping("/product/coupon/inner/issue")
    ResponseDto<Object> issue(@RequestParam("uId") Integer uId,
                              @RequestParam("name") String name,
                              @RequestParam("thresholdAmount") BigDecimal thresholdAmount,
                              @RequestParam("offAmount") BigDecimal offAmount,
                              @RequestParam("validDays") int validDays);
}
