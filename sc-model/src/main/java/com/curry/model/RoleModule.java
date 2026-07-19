package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * 角色-权限关联表
 */
@Data
@TableName("t_role_module")
public class RoleModule {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("role_id")
    @JsonProperty("roleId")
    private Integer roleId;

    @TableField("module_id")
    @JsonProperty("moduleId")
    private Integer moduleId;

    @TableField("create_time")
    @JsonProperty("createTime")
    private Date createTime;
}
