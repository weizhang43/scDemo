package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 字典数据实体：字典类型下的具体键值对，如"用户状态"类型下包含"启用=1""停用=0"。
 */
@Data
@TableName("sys_dict_data")
public class SysDictData {
    /** 数据主键 */
    @TableId(type = IdType.AUTO)
    @TableField("data_id")
    private Long dataId;
    /** 所属字典类型编码 */
    @TableField("dict_type")
    private String dictType;
    /** 字典标签（展示名） */
    @TableField("dict_label")
    private String dictLabel;
    /** 字典值 */
    @TableField("dict_value")
    private String dictValue;
    /** 排序号 */
    @TableField("sort_order")
    private Integer sortOrder;
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