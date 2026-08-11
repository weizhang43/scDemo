package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@TableName("t_user")
public class User {
    @TableId(value = "u_id", type = IdType.AUTO)
    @JsonProperty("uId")
    private Integer uId;

    @JsonProperty("uName")
    @TableField("u_name")
    private String uName;

    @TableField("password")
    private String password;

    @JsonProperty("realName")
    @TableField("real_name")
    private String realName;

    @TableField("gender")
    private Integer gender;

    @TableField("phone")
    private String phone;

    @JsonProperty("birthday")
    @TableField("birthday")
    private String birthday;

    /** 头像图片路径，形如 /user/image/xxx.png，由 FileController 上传后返回 */
    @TableField("avatar")
    private String avatar;

    @TableField("email")
    private String email;

    @JsonProperty("uType")
    @TableField("u_type")
    private Integer uType;

    /** 逻辑删除标志 0-正常 1-已删除，MyBatis-Plus 自动在 wrapper 查询/更新时过滤 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
