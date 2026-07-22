package com.example.scuser.controller;

import com.curry.model.OperationLog;
import com.example.scuser.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

@RestController
@RequestMapping("/user/operationLog")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    /** 分页查询操作日志，时间格式 yyyy-MM-dd HH:mm:ss */
    @GetMapping("/page")
    public ResponseDto<OperationLog> page(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "uName", required = false) String uName,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "opType", required = false) String opType,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "beginTime", required = false) String beginTime,
            @RequestParam(value = "endTime", required = false) String endTime) {
        return operationLogService.page(pageNum, pageSize, uName, module, opType,
                status, beginTime, endTime);
    }
}
