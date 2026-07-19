package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 权限/资源表(树形结构)
 */
@Data
@TableName("t_module")
public class Module {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("parent_id")
    @JsonProperty("parentId")
    private Integer parentId;

    @TableField("name")
    private String name;

    @TableField("type")
    private String type;

    @TableField("permission")
    private String permission;

    @TableField("url")
    private String url;

    @TableField("icon")
    private String icon;

    @TableField("sort")
    private Integer sort;

    @TableField("status")
    private Integer status;

    @TableField("create_time")
    @JsonProperty("createTime")
    private Date createTime;

    @TableField("update_time")
    @JsonProperty("updateTime")
    private Date updateTime;

    /** 子节点(前端构建树用,非数据库字段) */
    @TableField(exist = false)
    @JsonProperty("children")
    private List<Module> children;
}
