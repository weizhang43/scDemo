package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.Module;
import response.ResponseDto;

import java.util.List;

public interface ModuleService extends IService<Module> {

    /** 全部权限(平铺) */
    ResponseDto<Module> listAll();

    /** 树形结构返回 */
    ResponseDto<Module> listTree();

    ResponseDto<Module> add(Module module);

    ResponseDto<Module> update(Module module);

    ResponseDto<Module> remove(Integer id);
}
