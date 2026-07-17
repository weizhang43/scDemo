package com.example.scorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
}


