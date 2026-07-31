package com.curry.scjob.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import response.ResponseDto;

@Component
@FeignClient(value = "sc-product", path = "/sc-product")
public interface ProductFeignService {

    /**
     * 将 Redis 中缓存的点赞数批量幂等回写到 t_product，返回回写数量。
     */
    @PostMapping("/product/job/flushLikeCount")
    ResponseDto<Integer> flushLikeCount();

    /**
     * 标记已过期商品并把已过期商品统一下架，返回执行摘要。
     */
    @PostMapping("/product/job/markExpired")
    ResponseDto<String> markExpired();

    /**
     * AI 补商品描述并同步 ES，返回执行摘要。
     */
    @PostMapping("/product/job/fillProDesc")
    ResponseDto<String> fillProDesc();

    /**
     * 全量重建商品描述 ES 索引，返回执行摘要。
     */
    @PostMapping("/product/job/rebuildProDescIndex")
    ResponseDto<String> rebuildProDescIndex();

    /**
     * 为无图商品随机绑定本地图片，返回执行摘要。
     */
    @PostMapping("/product/job/dealProductImage")
    ResponseDto<String> dealProductImage();
}
