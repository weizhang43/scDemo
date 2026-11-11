package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 字典类型实体：定义字典分类，如"用户状态""订单状态"等。
 */
@Data
@TableName("sys_dict_type")
public class SysDictType {
    /** 字典类型主键 */
    @TableId(type = IdType.AUTO)
    @TableField("dict_id")
    private Long dictId;
    /** 字典名称 */
    @TableField("dict_name")
    private String dictName;
    /** 字典类型编码 */
    @TableField("dict_type")
    private String dictType;
    /** 状态：1启用 0停用 */
    @TableField("status")
    private Integer status;
    /** 创建时间 */
    @TableField("create_time")
    private Date createTime;
    /** 更新时间 */
    @TableField("update_time")
    private Date updateTime;
    /** 备注 */
    @TableField("remark")
    private String remark;
}