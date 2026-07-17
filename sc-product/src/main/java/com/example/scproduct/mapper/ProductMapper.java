package com.example.scproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
