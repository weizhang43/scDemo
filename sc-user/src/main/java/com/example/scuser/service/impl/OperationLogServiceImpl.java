package com.example.scuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.OperationLog;
import com.example.scuser.mapper.OperationLogMapper;
import com.example.scuser.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import response.ResponseDto;

@Slf4j
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog>
        implements OperationLogService {

    @Async
    @Override
    public void saveAsync(OperationLog operationLog) {
        try {
            baseMapper.insert(operationLog);
        } catch (Exception e) {
            log.error("[OperationLog] 保存操作日志失败 log={}", operationLog, e);
        }
    }

    @Override
    public ResponseDto<OperationLog> page(Integer pageNum, Integer pageSize,
                                          String uName, String module, String opType,
                                          Integer status, String beginTime, String endTime) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .like(StringUtils.hasText(uName), OperationLog::getUName, uName)
                .eq(StringUtils.hasText(module), OperationLog::getModule, module)
                .eq(StringUtils.hasText(opType), OperationLog::getOpType, opType)
                .eq(status != null, OperationLog::getStatus, status)
                .ge(StringUtils.hasText(beginTime), OperationLog::getCreateTime, beginTime)
                .le(StringUtils.hasText(endTime), OperationLog::getCreateTime, endTime)
                .orderByDesc(OperationLog::getLogId);
        Page<OperationLog> page = baseMapper.selectPage(
                new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), wrapper);
        return ResponseDto.success(page);
    }
}
