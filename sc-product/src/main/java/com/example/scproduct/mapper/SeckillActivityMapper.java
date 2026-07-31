package com.example.scproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.curry.model.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    /**
     * 分页查询秒杀活动，联表带出商品名/原价/库存，并按可见范围过滤。
     * 归属只存在于 t_product.merchant_id，所以必须 join 才能判定商家是否可见。
     *
     * @param pId        非 null 时只查该商品的活动
     * @param merchantId 非 null 时只查该商家的商品与公共商品的活动
     */
    @Select("<script>" +
            "SELECT s.*, p.p_name AS pName, p.price AS price, p.image_url AS imageUrl, " +
            "       p.stock AS productStock " +
            "FROM t_seckill_activity s JOIN t_product p ON p.p_id = s.p_id " +
            "<where>" +
            "  <if test='pId != null'> AND s.p_id = #{pId} </if>" +
            "  <if test='merchantId != null'> AND (p.merchant_id = #{merchantId} OR p.merchant_id IS NULL) </if>" +
            "</where>" +
            "ORDER BY s.start_time DESC, s.id DESC" +
            "</script>")
    IPage<SeckillActivity> selectActivityPage(IPage<SeckillActivity> page,
                                             @Param("pId") Integer pId,
                                             @Param("merchantId") Integer merchantId);

    /**
     * 顾客端：尚未结束且商品在架的有效活动（含未开始的，供前端做倒计时）。
     */
    @Select("SELECT s.*, p.p_name AS pName, p.price AS price, p.image_url AS imageUrl, " +
            "       p.stock AS productStock " +
            "FROM t_seckill_activity s JOIN t_product p ON p.p_id = s.p_id " +
            "WHERE s.status = 1 AND s.end_time >= NOW() AND p.status = 1 AND p.is_expired = 0 " +
            "ORDER BY s.start_time ASC, s.id ASC")
    List<SeckillActivity> selectUpcomingAndRunning();

    /**
     * 按主键查活动并带出商品名/原价：秒杀落库时价格与名称快照都取自这里。
     */
    @Select("SELECT s.*, p.p_name AS pName, p.price AS price, p.image_url AS imageUrl, " +
            "       p.stock AS productStock " +
            "FROM t_seckill_activity s JOIN t_product p ON p.p_id = s.p_id WHERE s.id = #{id}")
    SeckillActivity selectDetailById(@Param("id") Integer id);

    /**
     * 同一商品在给定时间窗内已划出的秒杀名额总量，排除某个活动（更新场景）。
     * 用于校验「多个活动划出总量不得超过商品库存」。
     */
    @Select("<script>" +
            "SELECT COALESCE(SUM(seckill_stock), 0) FROM t_seckill_activity " +
            "WHERE p_id = #{pId} AND status = 1 AND end_time &gt;= NOW() " +
            "<if test='excludeId != null'> AND id != #{excludeId} </if>" +
            "</script>")
    int sumReservedStock(@Param("pId") Integer pId, @Param("excludeId") Integer excludeId);
}
