package com.curry.model;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_operation_log")
public class OperationLog {

    @TableId(value = "log_id", type = IdType.AUTO)
    @ExcelProperty("日志ID")
    private Long logId;

    @JsonProperty("uId")
    @TableField("u_id")
    @ExcelProperty("用户ID")
    private Integer uId;

    @JsonProperty("uName")
    @TableField("u_name")
    @ExcelProperty("用户名")
    private String uName;

    @TableField("module")
    @ExcelProperty("模块")
    private String module;

    @TableField("op_type")
    @ExcelProperty("操作类型")
    private String opType;

    @TableField("description")
    @ExcelProperty("描述")
    private String description;

    @TableField("method")
    @ExcelProperty("方法")
    private String method;

    @TableField("request_uri")
    @ExcelProperty("请求URI")
    private String requestUri;

    @TableField("request_method")
    @ExcelProperty("请求方式")
    private String requestMethod;

    @TableField("request_params")
    @ExcelProperty("请求参数")
    private String requestParams;

    @TableField("response_summary")
    @ExcelProperty("响应摘要")
    private String responseSummary;

    @TableField("ip")
    @ExcelProperty("IP")
    private String ip;

    @TableField("cost_ms")
    @ExcelProperty("耗时(ms)")
    private Long costMs;

    /** 执行结果 1-成功 0-失败 */
    @TableField("status")
    @ExcelProperty("执行结果")
    private Integer status;

    @TableField("error_msg")
    @ExcelProperty("错误信息")
    private String errorMsg;

    @TableField("create_time")
    @ExcelProperty("创建时间")
    private Date createTime;
}
