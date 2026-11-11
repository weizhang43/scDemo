package com.example.scuser.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.curry.model.LoginLog;
import com.curry.model.auth.AuthConstant;
import com.example.scuser.mapper.LoginLogMapper;
import com.example.scuser.service.LoginLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

import java.util.Date;

/**
 * 登录日志：管理员可查看所有用户的登录记录，支持按用户名/IP/时间范围检索。
 */
@RestController
@RequestMapping("/user/loginLog")
public class LoginLogController {

    @Autowired
    private LoginLogService loginLogService;

    /** 管理员按用户名/IP/时间范围分页查询登录日志。 */
    @GetMapping("/page")
    public ResponseDto<LoginLog> page(
            @RequestParam(value = "uName", required = false) String uName,
            @RequestParam(value = "ip", required = false) String ip,
            @RequestParam(value = "loginTimeStart", required = false) String loginTimeStart,
            @RequestParam(value = "loginTimeEnd", required = false) String loginTimeEnd,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uType == null || uType != AuthConstant.U_TYPE_ADMIN) {
            return ResponseDto.error("无权限");
        }
        return loginLogService.pageQuery(uName, ip, loginTimeStart, loginTimeEnd, pageNum, pageSize);
    }
}