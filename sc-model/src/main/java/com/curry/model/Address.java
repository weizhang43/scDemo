package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_user_address")
public class Address {
    @TableId(value = "a_id", type = IdType.AUTO)
    @JsonProperty("aId")
    private Integer aId;

    @JsonProperty("uId")
    @TableField("u_id")
    private Integer uId;

    @TableField("consignee")
    private String consignee;

    @TableField("phone")
    private String phone;

    @TableField("province")
    private String province;

    @TableField("city")
    private String city;

    @TableField("district")
    private String district;

    @TableField("detail")
    private String detail;

    @JsonProperty("isDefault")
    @TableField("is_default")
    private Integer isDefault;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("create_time")
    private Date createTime;
}
