package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.User;
import com.example.scuser.dto.RegisterRequest;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;

public interface UserService extends IService<User> {

    /**
     * 用户注册
     */
    ResponseDto<User> register(RegisterRequest request);

    /**
     * 注册前发送邮箱验证码（六位数字，3 分钟内有效）
     */
    ResponseDto<User> sendEmailCode(String email);

    /**
     * 用户登录。expectedUType 非空时校验账号类型与登录入口一致，不一致直接拒绝。
     */
    ResponseDto<User> login(String uName, String password, Integer expectedUType);

    /**
     * 分页查询用户
     */
    ResponseDto<User> queryUser(String key, Integer gender, String birthdayStart,
                                String birthdayEnd, int pageNo, int pageSize);

    /**
     * 发送短信验证码（模拟）：根据手机号定位用户并生成验证码
     */
    ResponseDto<User> sendSmsCode(String phone);

    /**
     * 校验验证码并重置密码
     */
    ResponseDto<User> resetPassword(String phone, String code, String newPassword);

    /**
     * 根据用户ID查询用户基本信息（不含密码）
     */
    ResponseDto<User> getDetailById(Integer uId);

    /**
     * 修改用户基本信息（realName/gender/phone/birthday）
     */
    ResponseDto<User> updateProfile(User user);

    /**
     * 管理员驾驶舱：用户总量、按类型构成、今日新增注册数。
     */
    ResponseDto<User> statisticsOverview();

    /**
     * 按查询条件导出用户列表为 Excel（EasyExcel）
     */
    void export(String key, Integer gender, String birthdayStart, String birthdayEnd,
                HttpServletResponse response) throws Exception;
}
