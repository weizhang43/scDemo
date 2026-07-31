package com.example.scproduct.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.Product;
import com.curry.model.ProductPromotion;
import com.example.scproduct.auth.AudienceScope;
import response.ResponseDto;

import java.util.List;

public interface PromotionService extends IService<ProductPromotion> {

    /**
     * 创建折扣活动。校验：商品存在且调用方有权管理、discount 取值 1-99、
     * 时间窗合法、且与该商品已有活动的时间窗不重叠。
     */
    ResponseDto<ProductPromotion> create(ProductPromotion promotion, AudienceScope scope);

    /**
     * 取消折扣活动（直接删行）。校验调用方有权管理对应商品。
     */
    ResponseDto<ProductPromotion> cancel(Integer id, AudienceScope scope);

    /**
     * 商家端：分页列出可见范围内商品的折扣活动，按开始时间倒序。
     * @param pId 非 null 时只看该商品
     */
    ResponseDto<ProductPromotion> pageQuery(Integer pId, int pageNo, int pageSize, AudienceScope scope);

    /**
     * 把生效中的折扣回填到商品的 discount / effectivePrice 瞬态字段。
     * 无生效折扣的商品 effectivePrice 等于原价，discount 为 null。
     */
    void fillEffectivePrice(List<Product> products);
}
