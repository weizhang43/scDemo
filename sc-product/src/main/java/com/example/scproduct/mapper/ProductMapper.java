package com.example.scproduct.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.curry.model.Product;
import com.example.scproduct.vo.ProductTypeCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 带成交数 / 评价数 / 点赞数的分页查询。四张表同库（schema zhangwei），直接联表，不走 Feign。
     * 成交口径与首页销量榜 OrderItemMapper.selectSalesRank 一致：order_status IN (1,2)。
     * 点赞数取 t_product_like 的行数而非 t_product.like_count 快照列 —— 后者靠 xxl-job 回写，
     * 任务没跑就是陈旧值，而明细表有唯一索引 uk_u_id_p_id，行数即精确点赞人数。
     * 所以 SELECT 必须逐列枚举（不能 p.*），否则 p.like_count 与聚合出来的同名列会撞车。
     * WHERE 全部由 ${ew.customSqlSegment} 从 wrapper 生成，applyScope 的权限过滤原样带入，不手写。
     * 派生表的键列必须起别名（s_pid / r_pid / l_pid），否则 wrapper 里的 p_id 条件会有歧义。
     * 排序走 choose 白名单分支而非字符串拼接，sortBy 无注入面；每个分支都带 p_id 次级键保证分页稳定。
     */
    @Select("<script>" +
            "SELECT p.p_id, p.p_name, p.price, p.stock, p.production_date, p.shelf_life, p.origin, " +
            "       p.is_expired, p.manufacturer, p.pro_desc, p.image_url, p.merchant_id, p.status, " +
            "       p.category_id, c.c_name AS category_name, " +
            "       COALESCE(s.sale_count, 0) AS sale_count, " +
            "       COALESCE(r.review_count, 0) AS review_count, " +
            "       COALESCE(l.like_total, 0) AS like_count " +
            "FROM t_product p " +
            // t_category 也有 status 列，直接联表会让 wrapper 的裸 status 条件产生歧义，
            // 所以走只含所需列的派生表，键列照例起别名
            "LEFT JOIN (SELECT id AS c_id, name AS c_name FROM t_category) c ON c.c_id = p.category_id " +
            "LEFT JOIN (SELECT i.p_id AS s_pid, SUM(i.quantity) AS sale_count " +
            "           FROM t_order_item i JOIN t_order o ON o.o_id = i.o_id " +
            "           WHERE o.order_status IN (1, 2) GROUP BY i.p_id) s ON s.s_pid = p.p_id " +
            "LEFT JOIN (SELECT p_id AS r_pid, COUNT(*) AS review_count " +
            "           FROM t_product_review GROUP BY p_id) r ON r.r_pid = p.p_id " +
            "LEFT JOIN (SELECT p_id AS l_pid, COUNT(*) AS like_total " +
            "           FROM t_product_like GROUP BY p_id) l ON l.l_pid = p.p_id " +
            "${ew.customSqlSegment} " +
            "<choose>" +
            "  <when test='sortBy == \"sales\"'>ORDER BY sale_count DESC, p.p_id DESC</when>" +
            "  <when test='sortBy == \"reviews\"'>ORDER BY review_count DESC, p.p_id DESC</when>" +
            // like_count 既是输出别名又是 t_product 的真实列名，这里写原始表达式避开歧义
            "  <when test='sortBy == \"likes\"'>ORDER BY COALESCE(l.like_total, 0) DESC, p.p_id DESC</when>" +
            "  <otherwise>ORDER BY p.p_id DESC</otherwise>" +
            "</choose>" +
            "</script>")
    IPage<Product> selectPageWithStats(IPage<Product> page,
                                       @Param(Constants.WRAPPER) Wrapper<Product> wrapper,
                                       @Param("sortBy") String sortBy);

    /**
     * 查询未过期、且将在 monthsAhead 个月内到期的商品。
     * 到期日 = 生产日期 + 保质期(天)。按到期日升序返回（最紧急在前）。
     *
     * @param merchantId 非 null 时只查该商家的商品与公共商品（merchant_id IS NULL）
     * @param onSaleOnly true 时只查上架商品
     */
    @Select("<script>" +
            "SELECT * FROM t_product " +
            "WHERE is_expired = 0 AND production_date IS NOT NULL AND shelf_life IS NOT NULL " +
            "AND DATE_ADD(production_date, INTERVAL shelf_life DAY) &gt;= CURDATE() " +
            "AND DATE_ADD(production_date, INTERVAL shelf_life DAY) " +
            "    &lt;= DATE_ADD(CURDATE(), INTERVAL #{monthsAhead} MONTH) " +
            "<if test='merchantId != null'> AND (merchant_id = #{merchantId} OR merchant_id IS NULL) </if>" +
            "<if test='onSaleOnly'> AND status = 1 </if>" +
            "ORDER BY DATE_ADD(production_date, INTERVAL shelf_life DAY) ASC" +
            "</script>")
    List<Product> selectExpiringWithin(@Param("monthsAhead") int monthsAhead,
                                       @Param("merchantId") Integer merchantId,
                                       @Param("onSaleOnly") boolean onSaleOnly);

    /**
     * 按一级分类分组计数：二级分类通过 IF(parent_id=0, id, parent_id) 归并到根分类。
     * category_id 为 NULL 或指向已删除分类的商品归入 categoryId=NULL 组（前端显示「未分类」）。
     * merchantId 非 null 时只统计该商家商品与公共商品（与商家可见范围一致）。
     */
    @Select("<script>" +
            "SELECT root.id AS categoryId, root.name AS categoryName, COUNT(*) AS cnt " +
            "FROM t_product p " +
            "LEFT JOIN t_category c ON c.id = p.category_id " +
            "LEFT JOIN t_category root ON root.id = IF(c.parent_id = 0, c.id, c.parent_id) " +
            "<if test='merchantId != null'> WHERE (p.merchant_id = #{merchantId} OR p.merchant_id IS NULL) </if>" +
            "GROUP BY root.id, root.name ORDER BY root.id" +
            "</script>")
    List<ProductTypeCountVO> selectCountByType(@Param("merchantId") Integer merchantId);

    @Update("<script>" +
            "  UPDATE t_product SET stock = stock + " +
            "    CASE p_id " +
            "      <foreach collection='list' item='item'>" +
            "        WHEN #{item.pId} THEN #{item.stock}" +
            "      </foreach>" +
            "    END " +
            "  WHERE p_id IN " +
            "    <foreach collection='list' item='item' open='(' separator=',' close=')'>" +
            "      #{item.pId}" +
            "    </foreach>" +
            "</script>")
    void batchUpdateStock(@Param("list") List<Product> products);
}
