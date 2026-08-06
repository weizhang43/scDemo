package com.example.scorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.OrderItem;
import com.example.scorder.vo.MonthlySalesVO;
import com.example.scorder.vo.TypeSalesVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /**
     * 批量插入订单商品记录。一条 SQL 完成，避免循环 insert 产生 N 次网络往返。
     */
    @Insert({"<script>",
            "INSERT INTO t_order_item(o_id, p_id, p_name, price, quantity) VALUES",
            "<foreach collection='list' item='it' separator=','>",
            "(#{it.oId}, #{it.pId}, #{it.pName}, #{it.price}, #{it.quantity})",
            "</foreach>",
            "</script>"})
    int insertBatch(@Param("list") List<OrderItem> list);

    /**
     * 商品销量排行：按商品聚合购买数量，仅统计已下单(1)/已完成(2)的订单。
     * 只按 p_id 分组——p_name 是每单快照，商品改名后同一商品会被拆成多行，故用 MAX 取一个。
     * merchantId 非 null 时只统计该商家商品与公共商品（商家工作台热销榜用）。
     */
    @Select({"<script>",
            "SELECT i.p_id AS pId, MAX(i.p_name) AS pName, SUM(i.quantity) AS salesCount",
            "FROM t_order_item i",
            "JOIN t_order o ON o.o_id = i.o_id",
            "<if test='merchantId != null'> JOIN t_product p ON p.p_id = i.p_id </if>",
            "WHERE o.order_status IN (1, 2)",
            "<if test='merchantId != null'> AND (p.merchant_id = #{merchantId} OR p.merchant_id IS NULL) </if>",
            "GROUP BY i.p_id",
            "ORDER BY salesCount DESC",
            "LIMIT #{limit}",
            "</script>"})
    List<Map<String, Object>> selectSalesRank(@Param("limit") int limit, @Param("merchantId") Integer merchantId);

    /**
     * 统计报表：销量按一级分类分组（同库直接联表，不走 Feign）。
     * 二级分类通过 IF(parent_id=0, id, parent_id) 归并到根分类；
     * category_id 为 NULL 或指向已删除分类的商品归入 categoryId=NULL 组（前端显示「未分类」）。
     * merchantId 非 null 时只统计该商家商品与公共商品（与商家可见范围一致）。
     */
    @Select({"<script>",
            "SELECT root.id AS categoryId, root.name AS categoryName, SUM(i.quantity) AS salesCount",
            "FROM t_order_item i",
            "JOIN t_order o ON o.o_id = i.o_id",
            "JOIN t_product p ON p.p_id = i.p_id",
            "LEFT JOIN t_category c ON c.id = p.category_id",
            "LEFT JOIN t_category root ON root.id = IF(c.parent_id = 0, c.id, c.parent_id)",
            "WHERE o.order_status IN (1, 2)",
            "<if test='merchantId != null'> AND (p.merchant_id = #{merchantId} OR p.merchant_id IS NULL) </if>",
            "GROUP BY root.id, root.name ORDER BY root.id",
            "</script>"})
    List<TypeSalesVO> selectTypeSales(@Param("merchantId") Integer merchantId);

    /**
     * 统计报表：近三个自然月（含当月）每月销量。无销量的月份不返回，由 Service 补 0。
     */
    @Select({"<script>",
            "SELECT DATE_FORMAT(o.create_time, '%Y-%m') AS month, SUM(i.quantity) AS salesCount",
            "FROM t_order_item i",
            "JOIN t_order o ON o.o_id = i.o_id",
            "JOIN t_product p ON p.p_id = i.p_id",
            "WHERE o.order_status IN (1, 2)",
            "AND o.create_time &gt;= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 2 MONTH), '%Y-%m-01')",
            "<if test='merchantId != null'> AND (p.merchant_id = #{merchantId} OR p.merchant_id IS NULL) </if>",
            "GROUP BY month ORDER BY month",
            "</script>"})
    List<MonthlySalesVO> selectMonthlySales(@Param("merchantId") Integer merchantId);

    /**
     * 商家口径今日成交：订单可能混含多商家商品，按明细归属统计。
     * 金额为明细原价快照合计（不含券抵扣），口径同 selectTypeSales。
     */
    @Select("SELECT IFNULL(SUM(i.price * i.quantity), 0) AS todayGmv, COUNT(DISTINCT o.o_id) AS todayOrderCount " +
            "FROM t_order_item i " +
            "JOIN t_order o ON o.o_id = i.o_id " +
            "JOIN t_product p ON p.p_id = i.p_id " +
            "WHERE o.order_status IN (1, 2, 3) AND o.create_time >= CURDATE() " +
            "AND (p.merchant_id = #{merchantId} OR p.merchant_id IS NULL)")
    Map<String, Object> selectTodayOverviewByMerchant(@Param("merchantId") Integer merchantId);

    /**
     * 商家口径近 N 天逐日成交（明细原价合计+去重单量）。无成交的日期不返回，由 Service 补 0。
     */
    @Select("SELECT DATE_FORMAT(o.create_time, '%Y-%m-%d') AS date, " +
            "IFNULL(SUM(i.price * i.quantity), 0) AS gmv, COUNT(DISTINCT o.o_id) AS orderCount " +
            "FROM t_order_item i " +
            "JOIN t_order o ON o.o_id = i.o_id " +
            "JOIN t_product p ON p.p_id = i.p_id " +
            "WHERE o.order_status IN (1, 2, 3) " +
            "AND o.create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} - 1 DAY) " +
            "AND (p.merchant_id = #{merchantId} OR p.merchant_id IS NULL) " +
            "GROUP BY date ORDER BY date")
    List<Map<String, Object>> selectDailySalesByMerchant(@Param("merchantId") Integer merchantId,
                                                         @Param("days") int days);
}


