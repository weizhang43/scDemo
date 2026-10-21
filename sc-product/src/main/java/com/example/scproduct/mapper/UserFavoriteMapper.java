package com.example.scproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scproduct.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    /**
     * 收藏列表：联商品现取名称/价格/主图/分类/上下架状态，商品被删不出现在列表。
     */
    @Select("SELECT f.id AS favorite_id, f.p_id, f.create_time AS favorite_time, " +
            "p.p_name, p.price, p.image_url, p.status, p.stock, p.category_id, p.is_expired " +
            "FROM t_user_favorite f JOIN t_product p ON p.p_id = f.p_id " +
            "WHERE f.u_id = #{uId} ORDER BY f.id DESC")
    List<Map<String, Object>> selectFavoriteList(@Param("uId") Integer uId);

    /**
     * 推荐加权：收藏类目权重 ×2（不限时间窗，收藏代表长期偏好）。
     */
    @Select("SELECT p.category_id AS categoryId, COUNT(*) * 2 AS weight " +
            "FROM t_user_favorite f JOIN t_product p ON p.p_id = f.p_id " +
            "WHERE f.u_id = #{uId} AND p.category_id IS NOT NULL " +
            "GROUP BY p.category_id")
    List<Map<String, Object>> selectFavoriteCategoryWeights(@Param("uId") Integer uId);
}
