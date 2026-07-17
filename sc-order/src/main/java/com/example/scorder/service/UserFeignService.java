package com.example.scorder.service;

import com.curry.model.Address;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import response.ResponseDto;

@Component
@FeignClient(value = "sc-user", path = "/sc-user")
public interface UserFeignService {

    /**
     * 根据地址主键拉取用户收货地址（订单服务用于落订单地址快照）。
     * @param aId 地址主键
     */
    @GetMapping("/user/address/{aId}")
    ResponseDto<Address> getAddress(@PathVariable("aId") Integer aId);
}
