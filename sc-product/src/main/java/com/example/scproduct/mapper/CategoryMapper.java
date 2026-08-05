package com.example.scproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scproduct.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    @Select("SELECT COUNT(*) FROM t_product WHERE category_id = #{id}")
    long countProductRef(@Param("id") Integer id);

    @Select("SELECT id FROM t_category WHERE parent_id = #{parentId}")
    List<Integer> selectChildIds(@Param("parentId") Integer parentId);
}
