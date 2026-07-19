package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * 用户-角色关联表
 */
@Data
@TableName("t_user_role")
public class UserRole {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("user_id")
    @JsonProperty("userId")
    private Integer userId;

    @TableField("role_id")
    @JsonProperty("roleId")
    private Integer roleId;

    @TableField("create_time")
    @JsonProperty("createTime")
    private Date createTime;
}
