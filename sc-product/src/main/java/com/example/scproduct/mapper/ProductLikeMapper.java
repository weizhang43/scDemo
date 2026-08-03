package com.example.scproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scproduct.entity.ProductLike;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductLikeMapper extends BaseMapper<ProductLike> {

    /**
     * 去重靠唯一索引 uk_u_id_p_id：INSERT IGNORE 一条语句完成「没点过才插」，
     * 返回 1 表示首次点赞、0 表示已点过。写成先 SELECT 再 INSERT 会把并发双击的竞态放回来。
     */
    @Insert("INSERT IGNORE INTO t_product_like(u_id, p_id) VALUES(#{uId}, #{pId})")
    int insertIgnore(@Param("uId") Integer uId, @Param("pId") Integer pId);

    /**
     * 某商品的实际点赞人数。唯一索引保证一人一行，所以这张明细表的行数就是精确点赞数，
     * 不依赖 t_product.like_count 快照列，也不依赖回写任务是否在跑。
     */
    @Select("SELECT COUNT(*) FROM t_product_like WHERE p_id = #{pId}")
    long countByPId(@Param("pId") Integer pId);

    /** 批量回读「我点过哪些」，供订单详情页一次性渲染按钮态，避免每行一个请求 */
    @Select({"<script>",
            "SELECT p_id FROM t_product_like WHERE u_id = #{uId} AND p_id IN",
            "<foreach collection='pIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>",
            "</script>"})
    List<Integer> selectLikedPIds(@Param("uId") Integer uId, @Param("pIds") List<Integer> pIds);
}
