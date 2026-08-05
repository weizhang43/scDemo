package com.example.scproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scproduct.entity.CouponTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponTemplateMapper extends BaseMapper<CouponTemplate> {

    /** 条件扣减剩余量：remain_count > 0 才扣，返回 0 表示已领罄（DB 兜底防超发） */
    @Update("UPDATE t_coupon_template SET remain_count = remain_count - 1 " +
            "WHERE id = #{id} AND remain_count > 0")
    int deductRemain(@Param("id") Integer id);

    /** 归还剩余量：领券落库失败的补偿路径，上限不超过发行总量 */
    @Update("UPDATE t_coupon_template SET remain_count = remain_count + 1 " +
            "WHERE id = #{id} AND remain_count < total_count")
    int restoreRemain(@Param("id") Integer id);
}
