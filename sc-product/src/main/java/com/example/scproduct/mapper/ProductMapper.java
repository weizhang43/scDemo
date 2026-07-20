package com.example.scproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

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
