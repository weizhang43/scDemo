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

    /**
     * 查询未过期、且将在 monthsAhead 个月内到期的商品。
     * 到期日 = 生产日期 + 保质期(天)。按到期日升序返回（最紧急在前）。
     */
    @Select("SELECT * FROM t_product " +
            "WHERE is_expired = 0 AND production_date IS NOT NULL AND shelf_life IS NOT NULL " +
            "AND DATE_ADD(production_date, INTERVAL shelf_life DAY) >= CURDATE() " +
            "AND DATE_ADD(production_date, INTERVAL shelf_life DAY) <= DATE_ADD(CURDATE(), INTERVAL #{monthsAhead} MONTH) " +
            "ORDER BY DATE_ADD(production_date, INTERVAL shelf_life DAY) ASC")
    List<Product> selectExpiringWithin(@Param("monthsAhead") int monthsAhead);

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
