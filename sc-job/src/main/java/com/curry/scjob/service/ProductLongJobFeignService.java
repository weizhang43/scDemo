package com.curry.scjob.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import response.ResponseDto;

/**
 * sc-product 长任务专用 Feign 客户端（AI 补描述 / ES 全量重建 / 图片绑定，可能运行数十分钟）。
 * 独立 contextId 以便单独配置超长 readTimeout，避免 30 分钟超时作为 default 波及普通短接口。
 */
@Component
@FeignClient(value = "sc-product", contextId = "sc-product-long-job", path = "/sc-product")
public interface ProductLongJobFeignService {

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
