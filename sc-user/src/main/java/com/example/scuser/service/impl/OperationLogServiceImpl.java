package com.example.scuser.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.OperationLog;
import com.example.scuser.mapper.OperationLogMapper;
import com.example.scuser.service.OperationLogService;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog>
        implements OperationLogService {

    public static final long PAGE_SIZE = 20000;

    @Autowired
    @Qualifier(value = "exportExecutor")
    private Executor exportExecutor;


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

    @Override
    public void export(String uName, String module, String opType, Integer status, String beginTime, String endTime, HttpServletResponse response) {
        long start = System.currentTimeMillis();
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .like(StringUtils.hasText(uName), OperationLog::getUName, uName)
                .eq(StringUtils.hasText(module), OperationLog::getModule, module)
                .eq(StringUtils.hasText(opType), OperationLog::getOpType, opType)
                .eq(status != null, OperationLog::getStatus, status)
                .ge(StringUtils.hasText(beginTime), OperationLog::getCreateTime, beginTime)
                .le(StringUtils.hasText(endTime), OperationLog::getCreateTime, endTime)
                .orderByDesc(OperationLog::getLogId);
        //List<OperationLog> operationLogList = getLogList(wrapper);
        //List<OperationLog> operationLogList = getLogList2(wrapper);
        List<OperationLog> operationLogList = getLogList3(wrapper);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = null;
        try {
            fileName = URLEncoder.encode("用户列表", "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
            EasyExcel.write(response.getOutputStream(), OperationLog.class)
                    .sheet("用户列表")
                    .doWrite(operationLogList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.info("导出共耗时：{}", System.currentTimeMillis() - start);

    }

    /**
     * 查找日志列表
     *
     * @param wrapper
     * @return
     */
    private List<OperationLog> getLogList(LambdaQueryWrapper<OperationLog> wrapper) {
        return baseMapper.selectList(wrapper);
    }

    /**
     * 分页查找日志列表
     *
     * @param wrapper
     * @return
     */
    private List<OperationLog> getLogList2(LambdaQueryWrapper<OperationLog> wrapper) {
        long count = baseMapper.selectCount(wrapper);
        long totalPage = count % PAGE_SIZE == 0 ? count / PAGE_SIZE : count / PAGE_SIZE + 1;
        List<OperationLog> operationLogList = Lists.newArrayList();
        for (int i = 1; i < totalPage + 1; i++) {
            Page<OperationLog> page = baseMapper.selectPage(
                    new Page<>(i, PAGE_SIZE), wrapper);
            operationLogList.addAll(page.getRecords());
        }
        return operationLogList;
    }

    /**
     * 多线程查找日志列表
     *
     * @param wrapper
     * @return
     */
    private List<OperationLog> getLogList3(LambdaQueryWrapper<OperationLog> wrapper)  {
        List<OperationLog> list = Lists.newArrayList();
        long count = baseMapper.selectCount(wrapper);
        long totalPage = count % PAGE_SIZE == 0 ? count / PAGE_SIZE : count / PAGE_SIZE + 1;
        List<CompletableFuture<List<OperationLog>>> futureList = new ArrayList<>();
        for (int i = 1; i < totalPage + 1; i++) {
            int finalI = i;
            futureList.add(CompletableFuture.supplyAsync(() -> getRecords(wrapper, finalI, PAGE_SIZE), exportExecutor));
        }
        for(CompletableFuture<List<OperationLog>> completableFuture : futureList){
            try {
                list.addAll(completableFuture.get());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        return list;
    }


    /**
     * 分页查询日志信息
     * @param wrapper
     * @param pageNo
     * @param pageSize
     * @return
     */
    private List<OperationLog> getRecords(LambdaQueryWrapper<OperationLog> wrapper,int pageNo,long pageSize) {
        Page<OperationLog> page = baseMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return page.getRecords();
    }
}
