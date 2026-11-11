package com.example.scuser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.SysDictType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典类型 Mapper：基于 MyBatis-Plus BaseMapper 提供 CRUD 能力。
 */
@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictType> {
}