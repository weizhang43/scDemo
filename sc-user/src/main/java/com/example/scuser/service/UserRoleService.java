package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.UserRole;
import response.ResponseDto;

import java.util.List;

public interface UserRoleService extends IService<UserRole> {

    /** 查询某用户已绑定的角色ID集合 */
    ResponseDto<Integer> listRoleIdsByUserId(Integer userId);

    /** 给用户重新分配角色(先清空后批量插入),roleIds 为空则清空 */
    ResponseDto<Void> assignRoles(Integer userId, List<Integer> roleIds);

    /** 查询用户拥有的按钮权限标识集合（去重，结果在 dataList） */
    ResponseDto<String> listBtnPerms(Integer userId);
}
