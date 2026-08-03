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
     */
    @Select({
            "SELECT i.p_id AS pId, MAX(i.p_name) AS pName, SUM(i.quantity) AS salesCount",
            "FROM t_order_item i",
            "JOIN t_order o ON o.o_id = i.o_id",
            "WHERE o.order_status IN (1, 2)",
            "GROUP BY i.p_id",
            "ORDER BY salesCount DESC",
            "LIMIT #{limit}"
    })
    List<Map<String, Object>> selectSalesRank(@Param("limit") int limit);

    /**
     * 统计报表：销量按商品类型分组，联 t_product 现取 p_type（同库直接联表，不走 Feign）。
     * merchantId 非 null 时只统计该商家商品与公共商品（与商家可见范围一致）。
     */
    @Select({"<script>",
            "SELECT p.p_type AS pType, SUM(i.quantity) AS salesCount",
            "FROM t_order_item i",
            "JOIN t_order o ON o.o_id = i.o_id",
            "JOIN t_product p ON p.p_id = i.p_id",
            "WHERE o.order_status IN (1, 2)",
            "<if test='merchantId != null'> AND (p.merchant_id = #{merchantId} OR p.merchant_id IS NULL) </if>",
            "GROUP BY p.p_type ORDER BY p.p_type",
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
}


