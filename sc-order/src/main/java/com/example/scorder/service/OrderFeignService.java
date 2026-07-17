package com.example.scorder.service;

import com.curry.model.Address;
import com.curry.model.Product;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import response.ResponseDto;

import java.util.List;

@Component
@FeignClient(value = "sc-product", path = "/sc-product")
public interface OrderFeignService {

    @GetMapping("/product/getProduct")
    Product getProduct();

    @PostMapping("/product/createProduct")
    ResponseDto<Product> createProduct();

    @PostMapping("/product/deductStock")
    ResponseDto<Product> deductStock(@RequestBody List<Product> products);

    @PostMapping("/product/checkAndDeductStock")
    ResponseDto<Product> checkAndDeductStock(@RequestBody List<Product> items);

    @GetMapping("/product/listByIds")
    ResponseDto<Product> listByIds(@RequestParam("ids") List<Integer> ids);

    @PostMapping("/product/seckill/preDeduct")
    ResponseDto<Product> seckillPreDeduct(@RequestParam("pId") Integer pId,
                                          @RequestParam("uId") Integer uId);

    @PostMapping("/product/seckill/rollback")
    ResponseDto<Product> rollbackSeckillStock(@RequestParam("pId") Integer pId,
                                              @RequestParam("uId") Integer uId);
}
