package com.example.scuser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 按用户类型分组计数（管理员驾驶舱用户构成用） */
    @Select("SELECT u_type AS uType, COUNT(*) AS cnt FROM t_user WHERE deleted = 0 GROUP BY u_type")
    List<Map<String, Object>> countGroupByType();

    /** 今日新增注册数。create_time 为 NULL 的存量用户不计入 */
    @Select("SELECT COUNT(*) FROM t_user WHERE deleted = 0 AND create_time >= CURDATE()")
    Long countTodayNew();
}
