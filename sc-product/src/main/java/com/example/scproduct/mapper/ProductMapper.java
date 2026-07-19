package com.example.scproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Update("<script>" +
            "  <foreach collection='list' item='item' separator=';'>" +
            "    UPDATE t_product " +
            "    SET stock = stock + #{item.stock} " +
            "    WHERE p_id = #{item.pId}" +
            "  </foreach>" +
            "</script>")
    void batchUpdateStock(@Param("list") List<Product> products);
}
