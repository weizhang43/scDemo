package com.example.scuser.controller;

import com.example.scuser.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** 给用户重新分配角色,roleIds 为空则清空 */
    @PostMapping("/assignRoles")
    public ResponseDto<Void> assignRoles(@RequestParam("userId") Integer userId,
                                         @RequestBody List<Integer> roleIds) {
        return userRoleService.assignRoles(userId, roleIds);
    }
}
