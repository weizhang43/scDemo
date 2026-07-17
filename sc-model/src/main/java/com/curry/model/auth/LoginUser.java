package com.curry.model.auth;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录用户上下文（存入 Redis 与 JWT payload 的内容）
 */
@Data
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer uId;

    private String uName;

    private String realName;

    public static LoginUser of(Integer uId, String uName, String realName) {
        LoginUser u = new LoginUser();
        u.uId = uId;
        u.uName = uName;
        u.realName = realName;
        return u;
    }
}
