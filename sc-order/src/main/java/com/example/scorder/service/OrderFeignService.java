package com.example.scorder.service;

import com.curry.model.Product;
import com.curry.model.SeckillActivity;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import response.ResponseDto;

import java.util.List;

@Component
@FeignClient(value = "sc-product", path = "/sc-product")
public interface OrderFeignService {

    /**
     * 拉取单个商品（演示链路）。
     */
    @GetMapping("/product/getProduct")
    Product getProduct();

    /**
     * 扣减库存（旧版），不预校验，库存不足直接失败。
     * @param products 待扣减的商品列表（pId + stock）
     */
    @PostMapping("/product/deductStock")
    ResponseDto<Product> deductStock(@RequestBody List<Product> products);

    /**
     * 校验并扣减库存：库存不足时整批回滚并返回失败，配合 Seata 全局回滚。
     * @param items 待扣减商品列表（pId + quantity）
     */
    @PostMapping("/product/checkAndDeductStock")
    ResponseDto<Product> checkAndDeductStock(@RequestBody List<Product> items);

    /**
     * 按主键批量拉取「可售」商品（已下架的不返回），带生效中的折扣与折后价。
     * 顾客侧读路径与下单取价都走这里，避免下架商品漏出。
     * @param ids 商品 pId 列表
     */
    @GetMapping("/product/listSellableByIds")
    ResponseDto<Product> listSellableByIds(@RequestParam("ids") List<Integer> ids);

    /**
     * 秒杀预扣：Redis 原子扣活动名额并标记已购用户，防超卖与重复下单。
     * 名额按 activityId 计，同一商品的多场活动互不干扰。
     */
    @PostMapping("/product/seckill/preDeduct")
    ResponseDto<SeckillActivity> seckillPreDeduct(@RequestParam("activityId") Integer activityId,
                                                  @RequestParam("uId") Integer uId);

    /**
     * 回滚秒杀预扣，用于落库失败时补偿。
     * @param restoreStock true 归还名额；false 只移除已购标记（真实库存不足时用，
     *                     归还名额只会让下一个人撞同一堵墙，形成失败死循环）
     */
    @PostMapping("/product/seckill/rollback")
    ResponseDto<SeckillActivity> rollbackSeckillStock(@RequestParam("activityId") Integer activityId,
                                                      @RequestParam("uId") Integer uId,
                                                      @RequestParam("restoreStock") boolean restoreStock);

    /**
     * 拉取秒杀活动详情：秒杀订单的价格与商品名快照均取自这里，不采信调用方传入的价格。
     */
    @GetMapping("/product/seckill/detail/{id}")
    ResponseDto<SeckillActivity> getSeckillActivity(@PathVariable("id") Integer id);


    /**
     * 取消订单，添加原商品库存
     * @param products
     * @return
     */
    @PostMapping("/product/addStock")
    ResponseDto<Product> addStock(@RequestBody List<Product> products);
}
