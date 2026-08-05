package com.example.scproduct.service;

import com.example.scproduct.entity.Category;
import response.ResponseDto;

public interface CategoryService {

    /** 全量分类树（两级），一级按 sort 排序，children 内同理 */
    ResponseDto<Category> tree();

    ResponseDto<Category> create(Category category);

    ResponseDto<Category> update(Category category);

    ResponseDto<Category> remove(Integer id);
}
