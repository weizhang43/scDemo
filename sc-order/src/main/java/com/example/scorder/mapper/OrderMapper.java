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
     * 查询当日订单数量（用于生成流水号）
     */
    @Select("SELECT COUNT(*) FROM t_order WHERE order_no LIKE CONCAT('ORD', #{dayPrefix}, '%')")
    int countByDay(@Param("dayPrefix") String dayPrefix);

    /**
     * 基于 version + 前置 order_status 的 CAS 更新。
     * rows==0 表示状态已被其他请求变更或 version 不匹配，调用方据此做幂等返回。
     */
    @Update("UPDATE t_order SET order_status=#{targetStatus}, version=version+1, update_time=NOW() " +
            "WHERE o_id=#{id} AND order_status=#{expectStatus} AND version=#{version}")
    int casUpdateStatus(@Param("id") Integer id,
                        @Param("expectStatus") Integer expectStatus,
                        @Param("targetStatus") Integer targetStatus,
                        @Param("version") Integer version);

    /**
     * 联表分页查询：用 t_user.u_name 替换 t_order.add_person（add_person 存的是 uName）。
     * 关键字 key 同时匹配 u_name / real_name / add_person。
     * uId 非空时只查该顾客的订单（顾客侧强制归属过滤，商家/管理员传 null）。
     */
    @Select({
            "<script>",
            "SELECT o.o_id, o.order_no, o.create_time, o.order_address,",
            "       o.order_amount, o.order_status, o.u_id,",
            "       COALESCE(u.real_name, o.add_person) AS add_person",
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
