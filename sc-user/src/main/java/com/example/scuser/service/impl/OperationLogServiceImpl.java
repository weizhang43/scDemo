package com.example.scuser.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.OperationLog;
import com.example.scuser.mapper.OperationLogMapper;
import com.example.scuser.service.OperationLogService;
import com.example.scuser.vo.OperationLogExportVO;
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
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        ExcelWriter excelWriter = null;
        try {
            String fileName = URLEncoder.encode("操作日志", "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
            excelWriter = EasyExcel.write(response.getOutputStream(), OperationLogExportVO.class).build();
            WriteSheet writeSheet = EasyExcel.writerSheet("操作日志").build();
            long totalRows = exportByIdSlice(uName, module, opType, status, beginTime, endTime, excelWriter, writeSheet);
            log.info("[export] 总行数={}", totalRows);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (excelWriter != null) {
                excelWriter.finish();
            }
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
     * 按 log_id 范围切片并行查询（避免深分页和重复 count），主线程按序边取边写，
     * 不在内存中攒全量结果
     *
     * @return 导出总行数
     */
    private long exportByIdSlice(String uName, String module, String opType,
                                 Integer status, String beginTime, String endTime,
                                 ExcelWriter excelWriter, WriteSheet writeSheet) {
        long boundStart = System.currentTimeMillis();
        OperationLog minLog = baseMapper.selectOne(
                buildWrapper(uName, module, opType, status, beginTime, endTime)
                        .orderByAsc(OperationLog::getLogId)
                        .last("LIMIT 1"));
        OperationLog maxLog = baseMapper.selectOne(
                buildWrapper(uName, module, opType, status, beginTime, endTime)
                        .orderByDesc(OperationLog::getLogId)
                        .last("LIMIT 1"));
        log.info("[export] min/max定界耗时={}ms", System.currentTimeMillis() - boundStart);
        if (minLog == null || maxLog == null) {
            return 0;
        }
        long minId = minLog.getLogId();
        long maxId = maxLog.getLogId();

        List<CompletableFuture<List<OperationLogExportVO>>> futureList = new ArrayList<>();
        for (long startId = minId; startId <= maxId; startId += PAGE_SIZE) {
            long endId = Math.min(startId + PAGE_SIZE - 1, maxId);
            long finalStartId = startId;
            futureList.add(CompletableFuture.supplyAsync(
                    () -> {
                        long sliceStart = System.currentTimeMillis();
                        List<OperationLog> records = baseMapper.selectList(
                                buildWrapper(uName, module, opType, status, beginTime, endTime)
                                        .ge(OperationLog::getLogId, finalStartId)
                                        .le(OperationLog::getLogId, endId)
                                        .orderByDesc(OperationLog::getLogId));
                        // VO 转换放在工作线程，减轻主线程写出压力
                        List<OperationLogExportVO> voList = new ArrayList<>(records.size());
                        for (OperationLog record : records) {
                            voList.add(OperationLogExportVO.of(record));
                        }
                        log.info("[export] 分片[{}-{}] 线程={} 行数={} 耗时={}ms",
                                finalStartId, endId, Thread.currentThread().getName(),
                                voList.size(), System.currentTimeMillis() - sliceStart);
                        return voList;
                    },
                    exportExecutor));
        }

        long totalRows = 0;
        // 按 id 区间倒序逐片写出，保证整体 log_id 降序；写完即释放该片内存
        for (int i = futureList.size() - 1; i >= 0; i--) {
            try {
                List<OperationLogExportVO> voList = futureList.get(i).get();
                excelWriter.write(voList, writeSheet);
                totalRows += voList.size();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return totalRows;
    }

    private LambdaQueryWrapper<OperationLog> buildWrapper(String uName, String module, String opType,
                                                          Integer status, String beginTime, String endTime) {
        return new LambdaQueryWrapper<OperationLog>()
                .like(StringUtils.hasText(uName), OperationLog::getUName, uName)
                .eq(StringUtils.hasText(module), OperationLog::getModule, module)
                .eq(StringUtils.hasText(opType), OperationLog::getOpType, opType)
                .eq(status != null, OperationLog::getStatus, status)
                .ge(StringUtils.hasText(beginTime), OperationLog::getCreateTime, beginTime)
                .le(StringUtils.hasText(endTime), OperationLog::getCreateTime, endTime);
    }


    /**
     * 分页查询日志信息（不执行 count）
     * @param wrapper
     * @param pageNo
     * @param pageSize
     * @return
     */
    private List<OperationLog> getRecords(LambdaQueryWrapper<OperationLog> wrapper,int pageNo,long pageSize) {
        Page<OperationLog> page = baseMapper.selectPage(new Page<>(pageNo, pageSize, false), wrapper);
        return page.getRecords();
    }
}
