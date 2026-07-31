package com.example.scorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scorder.entity.CartItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * 原子合并加购：依赖唯一索引 uk_u_id_p_id，连点两次加购不会各读到旧值丢掉一次累加。
     * maxQuantity 由 Service 用实时库存算出，LEAST 保证合并后不超上限 ——
     * 上限在 SQL 里截断而非 Java 里截断，才不会把 read-then-write 竞态重新引进来。
     */
    @Insert({"INSERT INTO t_cart_item(u_id, p_id, quantity) VALUES(#{uId}, #{pId}, #{quantity})",
            "ON DUPLICATE KEY UPDATE quantity = LEAST(quantity + #{quantity}, #{maxQuantity})"})
    int upsertAccumulate(@Param("uId") Integer uId, @Param("pId") Integer pId,
                         @Param("quantity") Integer quantity, @Param("maxQuantity") Integer maxQuantity);

    /**
     * 覆盖式改数量（非累加）。同样用 LEAST 兜住库存上限。
     */
    @Update({"UPDATE t_cart_item SET quantity = LEAST(#{quantity}, #{maxQuantity})",
            "WHERE u_id = #{uId} AND p_id = #{pId}"})
    int updateQuantity(@Param("uId") Integer uId, @Param("pId") Integer pId,
                       @Param("quantity") Integer quantity, @Param("maxQuantity") Integer maxQuantity);

    /**
     * 回读落库数量：既用于判断是否被 LEAST 截断，也用于判断该商品是否已在车里。
     */
    @Select("SELECT quantity FROM t_cart_item WHERE u_id = #{uId} AND p_id = #{pId}")
    Integer selectQuantity(@Param("uId") Integer uId, @Param("pId") Integer pId);

    /**
     * 归属校验内嵌 WHERE：越权删除只会影响 0 行，无需先查后判。
     * 按 (u_id, p_id) 删除天然幂等，结算后清车重试无害。
     */
    @Delete({"<script>",
            "DELETE FROM t_cart_item WHERE u_id = #{uId} AND p_id IN",
            "<foreach collection='pIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>",
            "</script>"})
    int deleteBatchOwn(@Param("uId") Integer uId, @Param("pIds") List<Integer> pIds);

    /**
     * 购物车条目数（商品种类数，非件数）—— 导航角标语义就是「几种商品」。
     */
    @Select("SELECT COUNT(1) FROM t_cart_item WHERE u_id = #{uId}")
    int countByUser(@Param("uId") Integer uId);
}
