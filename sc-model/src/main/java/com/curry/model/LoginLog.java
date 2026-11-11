package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * 登录日志实体：记录每次登录的用户名、角色、IP、时间及成功/失败状态。
 */
@Data
@TableName("login_log")
public class LoginLog {

    /** 主键自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    @JsonProperty("uName")
    @TableField("u_name")
    private String uName;

    /** 登录人角色：1商家 2顾客 3管理员 */
    @JsonProperty("uType")
    @TableField("u_type")
    private Integer uType;

    /** 登录IP */
    @TableField("ip")
    private String ip;

    /** 登录时间 */
    @JsonProperty("loginTime")
    @TableField("login_time")
    private Date loginTime;

    /** 登录状态：1成功 0失败 */
    @TableField("status")
    private Integer status;

    /** 备注信息（失败时记录原因） */
    @TableField("msg")
    private String msg;
}