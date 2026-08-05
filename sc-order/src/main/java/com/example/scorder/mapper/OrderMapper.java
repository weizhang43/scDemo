package com.example.scorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.curry.model.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 基于 version + 前置 order_status 的 CAS 更新。
     * rows==0 表示状态已被其他请求变更或 version 不匹配，调用方据此做幂等返回。
     */
    @Update("UPDATE t_order SET order_status=#{targetStatus}, version=version+1, update_time=NOW(), " +
            "receive_time = CASE WHEN #{targetStatus}=2 THEN NOW() ELSE receive_time END " +
            "WHERE o_id=#{id} AND order_status=#{expectStatus} AND version=#{version}")
    int casUpdateStatus(@Param("id") Integer id,
                        @Param("expectStatus") Integer expectStatus,
                        @Param("targetStatus") Integer targetStatus,
                        @Param("version") Integer version);

    /**
     * 商家发货 CAS：1(已支付)→3(已发货)，同一条 UPDATE 内写入快递信息与发货时间，
     * 避免"先改状态再补快递字段"两步之间被并发请求观察到中间态。
     */
    @Update("UPDATE t_order SET order_status=3, version=version+1, update_time=NOW(), " +
            "shipping_company=#{shippingCompany}, tracking_no=#{trackingNo}, ship_time=NOW() " +
            "WHERE o_id=#{id} AND order_status=1 AND version=#{version}")
    int casShip(@Param("id") Integer id,
                @Param("version") Integer version,
                @Param("shippingCompany") String shippingCompany,
                @Param("trackingNo") String trackingNo);

    /**
     * 联表分页查询：用 t_user.u_name 替换 t_order.add_person（add_person 存的是 uName）。
     * 关键字 key 同时匹配 u_name / real_name / add_person。
     * uId 非空时只查该顾客的订单（顾客侧强制归属过滤，商家/管理员传 null）。
     * 售后状态联 t_after_sale 现取（订单不设"售后中"状态，售后态由工单维护）。
     */
    @Select({
            "<script>",
            "SELECT o.o_id, o.order_no, o.create_time, o.order_address,",
            "       o.order_amount, o.order_status, o.u_id,",
            "       COALESCE(u.real_name, o.add_person) AS add_person,",
            "       a.status AS after_sale_status",
            "FROM t_order o",
            "LEFT JOIN t_user u ON o.add_person = u.u_name",
            "LEFT JOIN t_after_sale a ON a.o_id = o.o_id",
            "<where>",
            "  <if test='uId != null'>",
            "    AND o.u_id = #{uId}",
            "  </if>",
            "  <if test='key != null and key != \"\"'>",
            "    AND (u.u_name LIKE CONCAT('%', #{key}, '%')",
            "         OR u.real_name LIKE CONCAT('%', #{key}, '%')",
            "         OR o.add_person LIKE CONCAT('%', #{key}, '%'))",
            "  </if>",
            "  <if test='orderNo != null and orderNo != \"\"'>",
            "    AND o.order_no LIKE CONCAT('%', #{orderNo}, '%')",
            "  </if>",
            "  <if test='orderStatus != null'>",
            "    AND o.order_status = #{orderStatus}",
            "  </if>",
            "  <if test='createTimeStart != null'>",
            "    AND o.create_time &gt;= #{createTimeStart}",
            "  </if>",
            "  <if test='createTimeEnd != null'>",
            "    AND o.create_time &lt;= #{createTimeEnd}",
            "  </if>",
            "</where>",
            "ORDER BY o.create_time DESC, o.o_id DESC",
            "</script>"
    })
    IPage<Order> selectPageWithUserName(IPage<Order> page,
                                        @Param("key") String key,
                                        @Param("orderNo") String orderNo,
                                        @Param("orderStatus") Integer orderStatus,
                                        @Param("createTimeStart") java.util.Date createTimeStart,
                                        @Param("createTimeEnd") java.util.Date createTimeEnd,
                                        @Param("uId") Integer uId);

    /**
     * 按订单状态分组统计数量（用于列表状态 Tab 的数量徽标）。
     * 过滤条件与 selectPageWithUserName 一致，但不含 orderStatus 本身。
     */
    @Select({
            "<script>",
            "SELECT o.order_status AS orderStatus, COUNT(*) AS cnt",
            "FROM t_order o",
            "LEFT JOIN t_user u ON o.add_person = u.u_name",
            "<where>",
            "  <if test='uId != null'>",
            "    AND o.u_id = #{uId}",
            "  </if>",
            "  <if test='key != null and key != \"\"'>",
            "    AND (u.u_name LIKE CONCAT('%', #{key}, '%')",
            "         OR u.real_name LIKE CONCAT('%', #{key}, '%')",
            "         OR o.add_person LIKE CONCAT('%', #{key}, '%'))",
            "  </if>",
            "  <if test='orderNo != null and orderNo != \"\"'>",
            "    AND o.order_no LIKE CONCAT('%', #{orderNo}, '%')",
            "  </if>",
            "  <if test='createTimeStart != null'>",
            "    AND o.create_time &gt;= #{createTimeStart}",
            "  </if>",
            "  <if test='createTimeEnd != null'>",
            "    AND o.create_time &lt;= #{createTimeEnd}",
            "  </if>",
            "</where>",
            "GROUP BY o.order_status",
            "</script>"
    })
    List<Map<String, Object>> countGroupByStatus(@Param("key") String key,
                                                 @Param("orderNo") String orderNo,
                                                 @Param("createTimeStart") java.util.Date createTimeStart,
                                                 @Param("createTimeEnd") java.util.Date createTimeEnd,
                                                 @Param("uId") Integer uId);
}
