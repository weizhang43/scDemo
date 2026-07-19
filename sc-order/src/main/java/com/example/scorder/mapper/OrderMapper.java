package com.example.scorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.curry.model.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 查询当日订单数量（用于生成流水号）
     */
    @Select("SELECT COUNT(*) FROM t_order WHERE order_no LIKE CONCAT('ORD', #{dayPrefix}, '%')")
    int countByDay(@Param("dayPrefix") String dayPrefix);

    /**
     * 联表分页查询：用 t_user.u_name 替换 t_order.add_person（add_person 实际存的是 uId）。
     * 关键字 key 匹配 u_name / real_name，避免 add_person 数字无法 like 命中。
     */
    @Select({
            "<script>",
            "SELECT o.o_id, o.order_no, o.create_time, o.order_address,",
            "       o.order_amount, o.order_status,",
            "       COALESCE(u.real_name, o.add_person) AS add_person",
            "FROM t_order o",
            "LEFT JOIN t_user u ON o.add_person = u.u_name",
            "<where>",
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
            "ORDER BY o.create_time DESC, o.o_id DESC",
            "</script>"
    })
    IPage<Order> selectPageWithUserName(IPage<Order> page,
                                        @Param("key") String key,
                                        @Param("orderNo") String orderNo,
                                        @Param("createTimeStart") java.util.Date createTimeStart,
                                        @Param("createTimeEnd") java.util.Date createTimeEnd);
}
