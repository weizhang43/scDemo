package com.example.scuser.controller;

import com.curry.model.OperationLog;
import com.curry.model.auth.AuthConstant;
import com.example.scuser.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/user/operationLog")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    /** 服务间内部调用令牌，用于校验其它模块转发来的操作日志 */
    @Value("${" + AuthConstant.INNER_TOKEN_PROPERTY + ":}")
    private String innerToken;

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


    @GetMapping("/export")
    public void export(
            @RequestParam(value = "uName", required = false) String uName,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "opType", required = false) String opType,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "beginTime", required = false) String beginTime,
            @RequestParam(value = "endTime", required = false) String endTime, HttpServletResponse response) {
        operationLogService.export(uName, module, opType, status, beginTime, endTime ,response);
    }

    /**
     * 服务间内部接口：接收其它模块(sc-order/sc-product 等)转发来的操作日志并落库。
     * 该接口不经网关，故自行校验 X-Inner-Token;日志实体在来源服务的请求线程已组装完毕，此处原样保存。
     */
    @PostMapping("/inner/save")
    public ResponseDto<Void> innerSave(
            @RequestBody OperationLog operationLog,
            @RequestHeader(value = AuthConstant.HEADER_X_INNER_TOKEN, required = false) String token) {
        if (innerToken == null || innerToken.isEmpty() || !innerToken.equals(token)) {
            return ResponseDto.error("内部令牌校验失败");
        }
        operationLogService.saveAsync(operationLog);
        return ResponseDto.success();
    }


}
