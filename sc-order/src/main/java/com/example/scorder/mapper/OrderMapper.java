package com.example.scorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
}
