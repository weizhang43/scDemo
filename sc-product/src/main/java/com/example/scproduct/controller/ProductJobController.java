package com.example.scproduct.controller;

import com.example.scproduct.service.ProductJobService;
import com.example.scproduct.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

/**
 * 定时任务触发端点：由 sc-job 的 XXL-Job 处理器通过 Feign 调用。
 * 请求需携带 X-Inner-Token 内部令牌通过 InnerAuthFilter 校验。
 */
@RestController
@RequestMapping("/product/job")
public class ProductJobController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductJobService productJobService;

    /**
     * 将 Redis 中缓存的点赞数批量幂等回写到 t_product。
     */
    @PostMapping("/flushLikeCount")
    public ResponseDto<Integer> flushLikeCount() {
        return ResponseDto.success(productService.flushLikeCount());
    }

    /**
     * 扫描未过期商品，将生产日期+保质期 < 当前日期的标记为已过期，并把已过期商品统一下架。
     */
    @PostMapping("/markExpired")
    public ResponseDto<String> markExpired() {
        return ResponseDto.success(productService.markExpiredProducts());
    }

    /**
     * AI 补商品描述并同步 ES。
     */
    @PostMapping("/fillProDesc")
    public ResponseDto<String> fillProDesc() {
        return ResponseDto.success(productJobService.fillProDesc());
    }

    /**
     * 全量重建商品描述 ES 索引。
     */
    @PostMapping("/rebuildProDescIndex")
    public ResponseDto<String> rebuildProDescIndex() {
        return ResponseDto.success(productJobService.rebuildProDescIndex());
    }

    /**
     * 为无图商品随机绑定本地图片。
     */
    @PostMapping("/dealProductImage")
    public ResponseDto<String> dealProductImage() {
        return ResponseDto.success(productJobService.dealProductImage());
    }
}
