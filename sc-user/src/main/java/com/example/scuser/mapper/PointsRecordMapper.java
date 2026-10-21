package com.example.scuser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.PointsRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {

    /**
     * 当前余额：全部流水求和。
     */
    @Select("SELECT IFNULL(SUM(change_value), 0) FROM t_points_record WHERE u_id = #{uId}")
    int sumBalance(@Param("uId") Integer uId);

    /**
     * 累计获取：只合计正数流水（等级依据，不随消耗下降）。
     */
    @Select("SELECT IFNULL(SUM(change_value), 0) FROM t_points_record WHERE u_id = #{uId} AND change_value > 0")
    int sumTotalEarned(@Param("uId") Integer uId);
}
