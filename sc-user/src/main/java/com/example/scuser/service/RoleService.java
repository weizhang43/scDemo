package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.Role;
import response.ResponseDto;

import java.util.List;

public interface RoleService extends IService<Role> {

    ResponseDto<Role> listAll();

    ResponseDto<Role> add(Role role);

    ResponseDto<Role> update(Role role);

    ResponseDto<Role> remove(Integer id);

    /** 给角色重新授权,moduleIds 为空则清空 */
    ResponseDto<Void> assignModules(Integer roleId, List<Integer> moduleIds);

    /** 查询某角色已授权的权限ID集合 */
    ResponseDto<Integer> listModuleIdsByRoleId(Integer roleId);
}
