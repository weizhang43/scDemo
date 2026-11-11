package com.example.scuser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.LoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper：基于 MyBatis-Plus BaseMapper 提供 CRUD 能力。
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}