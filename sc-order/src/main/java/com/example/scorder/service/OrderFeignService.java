package com.example.scorder.service;

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

    /**
     * 拉取单个商品（演示链路）。
     */
    @GetMapping("/product/getProduct")
    Product getProduct();

    /**
     * 远程触发 sc-product 创建商品（演示链路）。
     */
    @PostMapping("/product/createProduct")
    ResponseDto<Product> createProduct();

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
     * 按主键批量拉取商品信息，用于补商品名称快照。
     * @param ids 商品 pId 列表
     */
    @GetMapping("/product/listByIds")
    ResponseDto<Product> listByIds(@RequestParam("ids") List<Integer> ids);

    /**
     * 秒杀预扣：Redis 原子扣库存并标记已购用户，防超卖与重复下单。
     */
    @PostMapping("/product/seckill/preDeduct")
    ResponseDto<Product> seckillPreDeduct(@RequestParam("pId") Integer pId,
                                          @RequestParam("uId") Integer uId);

    /**
     * 回滚秒杀预扣：恢复 Redis 库存并移除已购标记，用于落库失败时补偿。
     */
    @PostMapping("/product/seckill/rollback")
    ResponseDto<Product> rollbackSeckillStock(@RequestParam("pId") Integer pId,
                                              @RequestParam("uId") Integer uId);


    /**
     * 取消订单，添加原商品库存
     * @param products
     * @return
     */
    @PostMapping("/product/addStock")
    ResponseDto<Product> addStock(@RequestBody List<Product> products);
}
