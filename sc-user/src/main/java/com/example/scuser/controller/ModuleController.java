package com.example.scuser.controller;

import com.curry.model.Module;
import com.example.scuser.service.ModuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

@RestController
@RequestMapping("/user/module")
public class ModuleController {

    @Autowired
    private ModuleService moduleService;

    /** 全部权限(平铺) */
    @GetMapping("/list")
    public ResponseDto<Module> list() {
        return moduleService.listAll();
    }

    /** 树形结构返回 */
    @GetMapping("/tree")
    public ResponseDto<Module> tree() {
        return moduleService.listTree();
    }

    @PostMapping
    public ResponseDto<Module> add(@RequestBody Module module) {
        return moduleService.add(module);
    }

    @PutMapping
    public ResponseDto<Module> update(@RequestBody Module module) {
        return moduleService.update(module);
    }

    @DeleteMapping("/{id}")
    public ResponseDto<Module> remove(@PathVariable("id") Integer id) {
        return moduleService.remove(id);
    }
}
