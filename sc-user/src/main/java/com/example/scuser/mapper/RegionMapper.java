package com.example.scuser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.Region;
import org.apache.ibatis.annotations.Mapper;

/**
 * 区域 Mapper：基于 MyBatis-Plus BaseMapper 提供 CRUD 能力。
 */
@Mapper
public interface RegionMapper extends BaseMapper<Region> {
}