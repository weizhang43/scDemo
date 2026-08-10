package com.example.scproduct.controller;

import com.curry.model.ProductPromotion;
import com.curry.model.annotation.OpLog;
import com.example.scproduct.auth.AudienceResolver;
import com.example.scproduct.service.PromotionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

/**
 * 商品折扣活动。挂在 /product/promotion 下以复用现有网关路由与前端代理。
 */
@RestController
@RequestMapping("/product/promotion")
public class PromotionController {

    @Autowired
    private PromotionService promotionService;

    /** 商家端：折扣活动分页列表，pId 非空时只看某个商品 */
    @GetMapping("/pageQuery")
    public ResponseDto<ProductPromotion> pageQuery(
            @RequestParam(value = "pId", required = false) Integer pId,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return promotionService.pageQuery(pId, pageNo, pageSize, AudienceResolver.current());
    }

    /** 创建折扣活动 */
    @OpLog(module = "商品管理", type = OpLog.OpType.ADD, description = "创建折扣活动")
    @PostMapping
    public ResponseDto<ProductPromotion> create(@RequestBody ProductPromotion promotion) {
        return promotionService.create(promotion, AudienceResolver.current());
    }

    /** 取消折扣活动（删行，时间窗即真相源，不留 status） */
    @OpLog(module = "商品管理", type = OpLog.OpType.DELETE, description = "取消折扣活动")
    @DeleteMapping("/{id}")
    public ResponseDto<ProductPromotion> cancel(@PathVariable("id") Integer id) {
        return promotionService.cancel(id, AudienceResolver.current());
    }
}
