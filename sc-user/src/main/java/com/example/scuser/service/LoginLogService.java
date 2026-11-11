package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.LoginLog;
import response.ResponseDto;

/**
 * 登录日志服务接口：日志记录与分页查询。
 */
public interface LoginLogService extends IService<LoginLog> {

    /**
     * 管理员按用户名/IP/时间范围分页查询登录日志。
     */
    ResponseDto<LoginLog> pageQuery(String uName, String ip, String loginTimeStart,
                                    String loginTimeEnd, Integer pageNum, Integer pageSize);

    /**
     * 记录一次登录行为（成功或失败）。
     */
    void recordLogin(String uName, Integer uType, String ip, Integer status, String msg);
}