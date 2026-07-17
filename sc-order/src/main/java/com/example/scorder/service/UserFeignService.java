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

    @GetMapping("/user/address/{aId}")
    ResponseDto<Address> getAddress(@PathVariable("aId") Integer aId);
}
