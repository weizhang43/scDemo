package com.example.scuser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
