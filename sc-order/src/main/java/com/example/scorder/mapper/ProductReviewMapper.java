package com.example.scorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.scorder.entity.ProductReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ProductReviewMapper extends BaseMapper<ProductReview> {

    /**
     * 商品详情页的评价列表：评价人展示名与头像都不落库，按 u_id 联 t_user 现取（同 OrderMapper 处理 add_person 的做法）。
     * u_name / u_avatar 靠 MyBatis 驼峰自动映射落到实体上 @TableField(exist = false) 的 uName、uAvatar。
     * 用户被删时 LEFT JOIN 出 null，前端回落「匿名用户」+ 首字母圆。
     */
    @Select({
            "SELECT r.id, r.u_id, r.o_id, r.p_id, r.p_name, r.rating, r.content, r.create_time,",
            "       u.u_name, u.avatar AS u_avatar",
            "FROM t_product_review r",
            "LEFT JOIN t_user u ON r.u_id = u.u_id",
            "WHERE r.p_id = #{pId}",
            "ORDER BY r.create_time DESC, r.id DESC"
    })
    IPage<ProductReview> selectPageByProduct(IPage<ProductReview> page, @Param("pId") Integer pId);

    /** 该订单已评过哪些商品，供前端把已评行置灰 */
    @Select("SELECT p_id FROM t_product_review WHERE o_id = #{oId}")
    List<Integer> selectReviewedPIds(@Param("oId") Integer oId);

    /** 商品平均分，无评价时返回 null */
    @Select("SELECT ROUND(AVG(rating), 1) FROM t_product_review WHERE p_id = #{pId}")
    BigDecimal selectAvgRating(@Param("pId") Integer pId);
}
