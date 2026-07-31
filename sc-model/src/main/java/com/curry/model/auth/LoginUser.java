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

    /** 用户类型：1-商家 2-顾客 3-管理员，见 AuthConstant.U_TYPE_* */
    private Integer uType;

    /**
     * 工厂方法：基于用户 ID、用户名、真实姓名构造登录用户上下文。
     */
    public static LoginUser of(Integer uId, String uName, String realName) {
        return of(uId, uName, realName, null);
    }

    /**
     * 工厂方法：附带用户类型，用于下游按角色判定可见范围。
     */
    public static LoginUser of(Integer uId, String uName, String realName, Integer uType) {
        LoginUser u = new LoginUser();
        u.uId = uId;
        u.uName = uName;
        u.realName = realName;
        u.uType = uType;
        return u;
    }
}
