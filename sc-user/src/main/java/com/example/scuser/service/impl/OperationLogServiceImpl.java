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
import exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * 操作日志服务：异步落库、分页查询、并行切片导出。
 */
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog>
        implements OperationLogService {

    public static final long PAGE_SIZE = 20000;

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogServiceImpl.class);

    /** 分页查询默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 分页查询默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Autowired
    @Qualifier(value = "exportExecutor")
    private Executor exportExecutor;


    @Async
    @Override
    public void saveAsync(OperationLog operationLog) {
        try {
            baseMapper.insert(operationLog);
        } catch (Exception e) {
            LOGGER.error("[OperationLog] 保存操作日志失败 log={}", operationLog, e);
        }
    }

    @Override
    public ResponseDto<OperationLog> page(Integer pageNum, Integer pageSize,
                                          String uName, String module, String opType,
                                          Integer status, String beginTime, String endTime) {
        Page<OperationLog> page = new Page<>(pageNum == null ? DEFAULT_PAGE_NUM : pageNum,
                pageSize == null ? DEFAULT_PAGE_SIZE : pageSize);
        return ResponseDto.success(baseMapper.selectPageWithRealName(
                page, uName, module, opType, status, beginTime, endTime));
    }

    @Override
    public void export(String uName, String module, String opType, Integer status,
                       String beginTime, String endTime, HttpServletResponse response) {
        long start = System.currentTimeMillis();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        ExcelWriter excelWriter = null;
        try {
            String fileName = URLEncoder.encode("操作日志", "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
            excelWriter = EasyExcel.write(response.getOutputStream(), OperationLogExportVO.class).build();
            WriteSheet writeSheet = EasyExcel.writerSheet("操作日志").build();
            long totalRows = exportByIdSlice(uName, module, opType, status, beginTime, endTime,
                    excelWriter, writeSheet);
            LOGGER.info("[export] 总行数={}", totalRows);
        } catch (IOException e) {
            throw new BusinessException("操作日志导出失败", e);
        } finally {
            if (excelWriter != null) {
                excelWriter.finish();
            }
        }
        LOGGER.info("导出共耗时：{}", System.currentTimeMillis() - start);
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
        LOGGER.info("[export] min/max定界耗时={}ms", System.currentTimeMillis() - boundStart);
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
                    () -> querySlice(uName, module, opType, status, beginTime, endTime,
                            finalStartId, endId),
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
                throw new BusinessException("操作日志导出被中断", e);
            } catch (ExecutionException e) {
                throw new BusinessException("操作日志切片查询失败", e);
            }
        }
        return totalRows;
    }

    /**
     * 查询单个 log_id 分片并在工作线程完成 VO 转换，减轻主线程写出压力。
     */
    private List<OperationLogExportVO> querySlice(String uName, String module, String opType,
                                                  Integer status, String beginTime, String endTime,
                                                  long startId, long endId) {
        long sliceStart = System.currentTimeMillis();
        List<OperationLog> records = baseMapper.selectList(
                buildWrapper(uName, module, opType, status, beginTime, endTime)
                        .ge(OperationLog::getLogId, startId)
                        .le(OperationLog::getLogId, endId)
                        .orderByDesc(OperationLog::getLogId));
        List<OperationLogExportVO> voList = new ArrayList<>(records.size());
        for (OperationLog record : records) {
            voList.add(OperationLogExportVO.of(record));
        }
        LOGGER.info("[export] 分片[{}-{}] 线程={} 行数={} 耗时={}ms",
                startId, endId, Thread.currentThread().getName(),
                voList.size(), System.currentTimeMillis() - sliceStart);
        return voList;
    }

    /**
     * 按查询条件构建操作日志的查询包装器。
     */
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
}
