package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.OperationLog;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;

public interface OperationLogService extends IService<OperationLog> {

    /** 异步保存日志，失败只记 error，不影响业务 */
    void saveAsync(OperationLog operationLog);

    /** 分页查询，条件均可空 */
    ResponseDto<OperationLog> page(Integer pageNum, Integer pageSize,
                                   String uName, String module, String opType,
                                   Integer status, String beginTime, String endTime);

    /**
     * 导出用户日志
     * @param uName 用户名（模糊匹配，可空）
     * @param module 模块（可空）
     * @param opType 操作类型（可空）
     * @param status 状态（可空）
     * @param beginTime 起始时间（可空）
     * @param endTime 截止时间（可空）
     * @param response 输出 Excel 的响应对象
     */
    void export(String uName, String module, String opType, Integer status,
                String beginTime, String endTime, HttpServletResponse response);
}
