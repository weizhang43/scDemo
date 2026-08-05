package com.example.scproduct.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.scproduct.entity.Category;
import com.example.scproduct.mapper.CategoryMapper;
import com.example.scproduct.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private static final int STATUS_ENABLED = 1;

    /** 分类总量小（字典级数据），全量捞出后内存组树 */
    @Override
    public ResponseDto<Category> tree() {
        List<Category> all = baseMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, STATUS_ENABLED));
        Comparator<Category> order = Comparator
                .comparing(Category::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Category::getId);
        Map<Integer, List<Category>> byParent = all.stream()
                .collect(Collectors.groupingBy(Category::getParentId));
        List<Category> tops = byParent.getOrDefault(0, new ArrayList<>());
        tops.sort(order);
        for (Category top : tops) {
            List<Category> children = byParent.getOrDefault(top.getId(), new ArrayList<>());
            children.sort(order);
            top.setChildren(children);
        }
        return ResponseDto.success(tops);
    }

    @Override
    public ResponseDto<Category> create(Category category) {
        if (category == null || category.getName() == null || category.getName().trim().isEmpty()) {
            return ResponseDto.error("分类名称不能为空");
        }
        int parentId = category.getParentId() == null ? 0 : category.getParentId();
        if (parentId != 0) {
            Category parent = baseMapper.selectById(parentId);
            if (parent == null) {
                return ResponseDto.error("父分类不存在");
            }
            if (parent.getParentId() != null && parent.getParentId() != 0) {
                return ResponseDto.error("最多支持两级分类，不能在二级分类下再建子分类");
            }
        }
        Category record = new Category();
        record.setParentId(parentId);
        record.setName(category.getName().trim());
        record.setSort(category.getSort() == null ? 0 : category.getSort());
        record.setStatus(STATUS_ENABLED);
        baseMapper.insert(record);
        return ResponseDto.success(record);
    }

    /** 只允许改名称与排序：换父级会让已挂商品的层级语义悄悄漂移，不开放 */
    @Override
    public ResponseDto<Category> update(Category category) {
        if (category == null || category.getId() == null) {
            return ResponseDto.error("分类ID不能为空");
        }
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            return ResponseDto.error("分类名称不能为空");
        }
        Category db = baseMapper.selectById(category.getId());
        if (db == null) {
            return ResponseDto.error("分类不存在");
        }
        Category record = new Category();
        record.setId(category.getId());
        record.setName(category.getName().trim());
        record.setSort(category.getSort());
        return baseMapper.updateById(record) > 0
                ? ResponseDto.success(null)
                : ResponseDto.error("修改分类失败");
    }

    @Override
    public ResponseDto<Category> remove(Integer id) {
        if (id == null) {
            return ResponseDto.error("分类ID不能为空");
        }
        Category db = baseMapper.selectById(id);
        if (db == null) {
            return ResponseDto.error("分类不存在");
        }
        if (!baseMapper.selectChildIds(id).isEmpty()) {
            return ResponseDto.error("该分类下存在子分类，请先删除子分类");
        }
        long refCount = baseMapper.countProductRef(id);
        if (refCount > 0) {
            return ResponseDto.error("该分类下还有 " + refCount + " 件商品，请先调整商品分类");
        }
        return baseMapper.deleteById(id) > 0
                ? ResponseDto.success(null)
                : ResponseDto.error("删除分类失败");
    }
}
