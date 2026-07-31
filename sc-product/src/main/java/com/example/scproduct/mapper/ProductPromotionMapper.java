package com.example.scproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.curry.model.ProductPromotion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductPromotionMapper extends BaseMapper<ProductPromotion> {

    /**
     * 分页查询折扣活动，联表带出商品名与原价，并按可见范围过滤。
     * 归属只存在于 t_product.merchant_id，所以必须 join 才能判定商家是否可见。
     *
     * @param pId        非 null 时只查该商品的活动
     * @param merchantId 非 null 时只查该商家的商品与公共商品的活动
     */
    @Select("<script>" +
            "SELECT pr.*, p.p_name AS pName, p.price AS price " +
            "FROM t_product_promotion pr JOIN t_product p ON p.p_id = pr.p_id " +
            "<where>" +
            "  <if test='pId != null'> AND pr.p_id = #{pId} </if>" +
            "  <if test='merchantId != null'> AND (p.merchant_id = #{merchantId} OR p.merchant_id IS NULL) </if>" +
            "</where>" +
            "ORDER BY pr.start_time DESC, pr.id DESC" +
            "</script>")
    IPage<ProductPromotion> selectPromotionPage(IPage<ProductPromotion> page,
                                                @Param("pId") Integer pId,
                                                @Param("merchantId") Integer merchantId);
}
