package com.example.scproduct.controller;

import com.curry.model.annotation.OpLog;
import com.curry.model.auth.AuthConstant;
import com.example.scproduct.entity.Category;
import com.example.scproduct.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

/**
 * 商品分类（两级树）。挂在 /product/category 下复用现有网关路由与前端代理。
 * 分类是全局字典：树查询对所有登录用户开放（顾客画廊导航需要），增删改仅限商家/管理员。
 */
@RestController
@RequestMapping("/product/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping("/tree")
    public ResponseDto<Category> tree() {
        return categoryService.tree();
    }

    @OpLog(module = "商品管理", type = OpLog.OpType.ADD, description = "新增商品分类")
    @PostMapping
    public ResponseDto<Category> create(@RequestBody Category category,
                                        @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (isCustomer(uType)) {
            return ResponseDto.error("无权管理商品分类");
        }
        return categoryService.create(category);
    }

    @OpLog(module = "商品管理", type = OpLog.OpType.UPDATE, description = "修改商品分类")
    @PutMapping
    public ResponseDto<Category> update(@RequestBody Category category,
                                        @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (isCustomer(uType)) {
            return ResponseDto.error("无权管理商品分类");
        }
        return categoryService.update(category);
    }

    @OpLog(module = "商品管理", type = OpLog.OpType.DELETE, description = "删除商品分类")
    @DeleteMapping("/{id}")
    public ResponseDto<Category> remove(@PathVariable("id") Integer id,
                                        @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (isCustomer(uType)) {
            return ResponseDto.error("无权管理商品分类");
        }
        return categoryService.remove(id);
    }

    private static boolean isCustomer(Integer uType) {
        return uType != null && uType == AuthConstant.U_TYPE_CUSTOMER;
    }
}
