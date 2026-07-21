package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
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

    @TableField("email")
    private String email;
}
