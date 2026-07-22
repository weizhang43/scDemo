package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_operation_log")
public class OperationLog {

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    @TableField("u_id")
    private Integer uId;

    @TableField("u_name")
    private String uName;

    @TableField("module")
    private String module;

    @TableField("op_type")
    private String opType;

    @TableField("description")
    private String description;

    @TableField("method")
    private String method;

    @TableField("request_uri")
    private String requestUri;

    @TableField("request_method")
    private String requestMethod;

    @TableField("request_params")
    private String requestParams;

    @TableField("response_summary")
    private String responseSummary;

    @TableField("ip")
    private String ip;

    @TableField("cost_ms")
    private Long costMs;

    /** 执行结果 1-成功 0-失败 */
    @TableField("status")
    private Integer status;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("create_time")
    private Date createTime;
}
