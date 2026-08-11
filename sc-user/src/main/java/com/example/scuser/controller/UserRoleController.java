package com.example.scuser.controller;

import com.curry.model.auth.AuthConstant;
import com.example.scuser.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

import java.util.List;

@RestController
@RequestMapping("/user/userRole")
public class UserRoleController {

    @Autowired
    private UserRoleService userRoleService;

    /** 查询某用户已绑定的角色ID集合 */
    @GetMapping("/roleIds")
    public ResponseDto<Integer> listRoleIds(@RequestParam("userId") Integer userId) {
        return userRoleService.listRoleIdsByUserId(userId);
    }

    /** 当前登录用户的按钮权限标识集合（网关透传的 X-User-Id 定位用户） */
    @GetMapping("/perms")
    public ResponseDto<String> myPerms(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        return userRoleService.listBtnPerms(uId);
    }

    /** 给用户重新分配角色,roleIds 为空则清空 */
    @PostMapping("/assignRoles")
    public ResponseDto<Void> assignRoles(@RequestParam("userId") Integer userId,
                                         @RequestBody List<Integer> roleIds) {
        return userRoleService.assignRoles(userId, roleIds);
    }
}
