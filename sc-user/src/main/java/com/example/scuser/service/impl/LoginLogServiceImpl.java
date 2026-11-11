package com.example.scuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.LoginLog;
import com.example.scuser.mapper.LoginLogMapper;
import com.example.scuser.service.LoginLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import response.ResponseDto;

import java.util.Date;

/**
 * 登录日志服务：记录登录行为，支持管理员按用户名/IP/时间范围分页查询。
 */
@Service
public class LoginLogServiceImpl extends ServiceImpl<LoginLogMapper, LoginLog> implements LoginLogService {

    private static final int DEFAULT_PAGE_SIZE = 10;

    @Override
    public ResponseDto<LoginLog> pageQuery(String uName, String ip, String loginTimeStart,
                                           String loginTimeEnd, Integer pageNum, Integer pageSize) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(uName)) {
            wrapper.like(LoginLog::getUName, uName);
        }
        if (StringUtils.hasText(ip)) {
            wrapper.like(LoginLog::getIp, ip);
        }
        if (StringUtils.hasText(loginTimeStart)) {
            wrapper.ge(LoginLog::getLoginTime, loginTimeStart + " 00:00:00");
        }
        if (StringUtils.hasText(loginTimeEnd)) {
            wrapper.le(LoginLog::getLoginTime, loginTimeEnd + " 23:59:59");
        }
        wrapper.orderByDesc(LoginLog::getLoginTime);
        Page<LoginLog> page = baseMapper.selectPage(new Page<>(current, size), wrapper);
        ResponseDto<LoginLog> resp = new ResponseDto<>();
        resp.setCode(ResponseDto.SUCCESS_CODE);
        resp.setDaoResult(page);
        return resp;
    }

    @Override
    public void recordLogin(String uName, Integer uType, String ip, Integer status, String msg) {
        LoginLog log = new LoginLog();
        log.setUName(uName);
        log.setUType(uType);
        log.setIp(ip);
        log.setLoginTime(new Date());
        log.setStatus(status != null ? status : 1);
        log.setMsg(msg);
        baseMapper.insert(log);
    }
}