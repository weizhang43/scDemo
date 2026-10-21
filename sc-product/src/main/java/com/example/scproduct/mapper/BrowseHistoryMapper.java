package com.example.scproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scproduct.entity.BrowseHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface BrowseHistoryMapper extends BaseMapper<BrowseHistory> {

    /**
     * 足迹列表：联商品现取名称/价格/主图/分类，商品被删不出现在列表。
     */
    @Select("SELECT h.p_id, h.category_id, h.create_time AS browse_time, " +
            "p.p_name, p.price, p.image_url, p.status, p.stock, p.is_expired " +
            "FROM t_browse_history h JOIN t_product p ON p.p_id = h.p_id " +
            "WHERE h.u_id = #{uId} ORDER BY h.create_time DESC LIMIT #{limit}")
    List<Map<String, Object>> selectHistoryList(@Param("uId") Integer uId, @Param("limit") int limit);

    /**
     * 推荐加权：近 N 天足迹类目权重 ×1（短期兴趣）。
     */
    @Select("SELECT category_id AS categoryId, COUNT(*) AS weight " +
            "FROM t_browse_history " +
            "WHERE u_id = #{uId} AND category_id IS NOT NULL " +
            "AND create_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "GROUP BY category_id")
    List<Map<String, Object>> selectRecentCategoryWeights(@Param("uId") Integer uId,
                                                          @Param("days") int days);
}
