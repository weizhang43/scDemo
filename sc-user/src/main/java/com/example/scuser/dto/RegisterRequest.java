package com.example.scuser.dto;

import com.curry.model.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 注册请求体。显式列出可提交字段，避免直接绑定 User 实体导致 uId/uType 被请求体任意赋值
 * （/user/register 在网关白名单内，任何人都能匿名调用）。
 */
@Data
public class RegisterRequest {

    @JsonProperty("uName")
    private String uName;

    private String password;

    @JsonProperty("uType")
    private Integer uType;

    @JsonProperty("realName")
    private String realName;

    private Integer gender;

    private String phone;

    private String birthday;

    private String email;

    /** 邮箱验证码，不落库 */
    private String emailCode;

    public User toUser() {
        User user = new User();
        user.setUName(uName);
        user.setPassword(password);
        user.setUType(uType);
        user.setRealName(realName);
        user.setGender(gender);
        user.setPhone(phone);
        user.setBirthday(birthday);
        user.setEmail(email);
        return user;
    }
}
