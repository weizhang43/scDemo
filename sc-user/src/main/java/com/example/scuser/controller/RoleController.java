package com.example.scuser.controller;

import com.curry.model.Role;
import com.example.scuser.annotation.OpLog;
import com.example.scuser.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

import java.util.List;

@RestController
@RequestMapping("/user/role")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @GetMapping("/list")
    public ResponseDto<Role> list() {
        return roleService.listAll();
    }

    @OpLog(module = "角色管理", type = OpLog.OpType.ADD, description = "新增角色")
    @PostMapping
    public ResponseDto<Role> add(@RequestBody Role role) {
        return roleService.add(role);
    }

    @OpLog(module = "角色管理", type = OpLog.OpType.UPDATE, description = "修改角色")
    @PutMapping
    public ResponseDto<Role> update(@RequestBody Role role) {
        return roleService.update(role);
    }

    @OpLog(module = "角色管理", type = OpLog.OpType.DELETE, description = "删除角色")
    @DeleteMapping("/{id}")
    public ResponseDto<Role> remove(@PathVariable("id") Integer id) {
        return roleService.remove(id);
    }

    /** 查询某角色已授权的权限ID集合 */
    @GetMapping("/moduleIds")
    public ResponseDto<Integer> listModuleIds(@RequestParam("roleId") Integer roleId) {
        return roleService.listModuleIdsByRoleId(roleId);
    }

    /** 给角色授权,moduleIds 为权限ID集合 */
    @PostMapping("/assignModules")
    public ResponseDto<Void> assignModules(@RequestParam("roleId") Integer roleId,
                                           @RequestBody List<Integer> moduleIds) {
        return roleService.assignModules(roleId, moduleIds);
    }
}
