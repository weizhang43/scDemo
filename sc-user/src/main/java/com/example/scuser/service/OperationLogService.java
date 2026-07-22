package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.OperationLog;
import response.ResponseDto;

public interface OperationLogService extends IService<OperationLog> {

    /** 异步保存日志，失败只记 error，不影响业务 */
    void saveAsync(OperationLog operationLog);

    /** 分页查询，条件均可空 */
    ResponseDto<OperationLog> page(Integer pageNum, Integer pageSize,
                                   String uName, String module, String opType,
                                   Integer status, String beginTime, String endTime);
}
