package com.example.scproduct.service.impl;

import com.example.scproduct.service.ExportTaskService;
import com.example.scproduct.service.ProductService;
import com.example.scproduct.vo.ExportTaskStatus;
import com.example.scproduct.vo.ExportTaskVO;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class ExportTaskServiceImpl implements ExportTaskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportTaskServiceImpl.class);
    private static final String KEY_PREFIX = "export:product:task:";
    private static final long TTL_SECONDS = 3600L;
    private static final long FINAL_TTL_SECONDS = 1800L;

    /** 任务 Hash 字段名 */
    private static final String F_TASK_ID = "taskId";
    private static final String F_STATUS = "status";
    private static final String F_TOTAL = "total";
    private static final String F_PROCESSED = "processed";
    private static final String F_PROGRESS = "progress";
    private static final String F_FILE_NAME = "fileName";
    private static final String F_FILE_PATH = "filePath";
    private static final String F_ERROR_MSG = "errorMsg";
    private static final String F_CREATE_TIME = "createTime";
    private static final String F_START_TIME = "startTime";
    private static final String F_FINISH_TIME = "finishTime";

    /** 进度百分比满值 */
    private static final int PERCENT_FULL = 100;
    /** 下载文件流拷贝缓冲区大小（字节） */
    private static final int DOWNLOAD_BUFFER_SIZE = 8192;
    /** 线程池拒绝时的提示语 */
    private static final String MSG_EXPORT_BUSY = "导出任务繁忙，请稍后重试";

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ProductService productService;

    @Autowired
    @Qualifier("exportExecutor")
    private ThreadPoolTaskExecutor exportExecutor;

    @Value("${product.export.dir:./export-files}")
    private String exportDir;

    @Value("${product.export.max-rows:100000}")
    private long maxRows;

    @Value("${product.export.page-size:1000}")
    private int pageSize;

    @Override
    public ExportTaskVO submit(ProductService.ProductExportQuery query) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();

        RMap<String, String> map = redissonClient.getMap(KEY_PREFIX + taskId);
        map.put(F_TASK_ID, taskId);
        map.put(F_STATUS, ExportTaskStatus.PENDING.name());
        map.put(F_TOTAL, "0");
        map.put(F_PROCESSED, "0");
        map.put(F_PROGRESS, "0");
        map.put(F_FILE_NAME, "商品列表_" + taskId + ".xlsx");
        map.put(F_ERROR_MSG, "");
        map.put(F_CREATE_TIME, String.valueOf(now));
        map.put(F_START_TIME, "0");
        map.put(F_FINISH_TIME, "0");
        map.expire(TTL_SECONDS, TimeUnit.SECONDS);

        ExportTaskVO vo = toVO(map);
        try {
            exportExecutor.execute(() -> runExport(taskId, query));
        } catch (RejectedExecutionException e) {
            LOGGER.warn("导出任务提交被拒绝 taskId={}", taskId, e);
            map.put(F_STATUS, ExportTaskStatus.FAILED.name());
            map.put(F_ERROR_MSG, MSG_EXPORT_BUSY);
            map.put(F_FINISH_TIME, String.valueOf(System.currentTimeMillis()));
            map.expire(FINAL_TTL_SECONDS, TimeUnit.SECONDS);
            vo.setStatus(ExportTaskStatus.FAILED.name());
            vo.setErrorMsg(MSG_EXPORT_BUSY);
            return vo;
        }
        return vo;
    }

    private void runExport(String taskId, ProductService.ProductExportQuery query) {
        String key = KEY_PREFIX + taskId;
        RMap<String, String> map = redissonClient.getMap(key);
        long now = System.currentTimeMillis();
        map.put(F_STATUS, ExportTaskStatus.RUNNING.name());
        map.put(F_START_TIME, String.valueOf(now));

        String dirPath = new File(exportDir).getAbsolutePath();
        File dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            markFailed(map, "无法创建导出目录: " + dirPath);
            return;
        }
        String fileName = map.get(F_FILE_NAME);
        File file = new File(dir, fileName);
        String outputPath = file.getAbsolutePath();

        try {
            long written = productService.exportToPath(
                    query,
                    outputPath,
                    maxRows,
                    pageSize,
                    (processed, total) -> {
                        map.put(F_PROCESSED, String.valueOf(processed));
                        map.put(F_TOTAL, String.valueOf(total));
                        int progress = total <= 0 ? 0
                                : (int) Math.min(PERCENT_FULL, processed * PERCENT_FULL / total);
                        map.put(F_PROGRESS, String.valueOf(progress));
                    },
                    () -> ExportTaskStatus.CANCELED.name().equals(map.get(F_STATUS))
            );
            if (ExportTaskStatus.CANCELED.name().equals(map.get(F_STATUS))) {
                file.delete();
                return;
            }
            map.put(F_PROCESSED, String.valueOf(written));
            map.put(F_PROGRESS, String.valueOf(PERCENT_FULL));
            map.put(F_FILE_PATH, outputPath);
            map.put(F_STATUS, ExportTaskStatus.SUCCESS.name());
            map.put(F_FINISH_TIME, String.valueOf(System.currentTimeMillis()));
            map.expire(FINAL_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.warn("导出任务被中断 taskId={}", taskId, ie);
            file.delete();
            if (!ExportTaskStatus.CANCELED.name().equals(map.get(F_STATUS))) {
                map.put(F_STATUS, ExportTaskStatus.CANCELED.name());
                map.put(F_FINISH_TIME, String.valueOf(System.currentTimeMillis()));
            }
            map.expire(FINAL_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.error("导出任务失败 taskId={}", taskId, e);
            file.delete();
            markFailed(map, e.getMessage());
        }
    }

    private void markFailed(RMap<String, String> map, String msg) {
        map.put(F_STATUS, ExportTaskStatus.FAILED.name());
        map.put(F_ERROR_MSG, msg == null ? "导出失败" : msg);
        map.put(F_FINISH_TIME, String.valueOf(System.currentTimeMillis()));
        map.expire(FINAL_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public ExportTaskVO status(String taskId) {
        if (StringUtils.isEmpty(taskId)) {
            return null;
        }
        RMap<String, String> map = redissonClient.getMap(KEY_PREFIX + taskId);
        if (!map.isExists()) {
            return null;
        }
        return toVO(map);
    }

    @Override
    public ExportTaskVO cancel(String taskId) {
        RMap<String, String> map = redissonClient.getMap(KEY_PREFIX + taskId);
        if (!map.isExists()) {
            return null;
        }
        String current = map.get(F_STATUS);
        // 终态任务不可取消
        if (ExportTaskStatus.SUCCESS.name().equals(current)
                || ExportTaskStatus.FAILED.name().equals(current)
                || ExportTaskStatus.CANCELED.name().equals(current)) {
            return toVO(map);
        }
        map.put(F_STATUS, ExportTaskStatus.CANCELED.name());
        return toVO(map);
    }

    @Override
    public boolean download(String taskId, HttpServletResponse response) throws Exception {
        RMap<String, String> map = redissonClient.getMap(KEY_PREFIX + taskId);
        File file = resolveDownloadFile(map);
        if (file == null) {
            return false;
        }
        String fileName = map.get(F_FILE_NAME);
        if (StringUtils.isEmpty(fileName)) {
            fileName = file.getName();
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=UTF-8''" + encoded);
        response.setContentLengthLong(file.length());
        try (FileInputStream fis = new FileInputStream(file);
             OutputStream os = response.getOutputStream()) {
            byte[] buf = new byte[DOWNLOAD_BUFFER_SIZE];
            int n;
            while ((n = fis.read(buf)) != -1) {
                os.write(buf, 0, n);
            }
            os.flush();
        }
        return true;
    }

    /**
     * 校验任务可下载并定位导出文件：任务存在、状态为成功且文件在磁盘上，否则返回 null。
     */
    private File resolveDownloadFile(RMap<String, String> map) {
        if (!map.isExists() || !ExportTaskStatus.SUCCESS.name().equals(map.get(F_STATUS))) {
            return null;
        }
        String filePath = map.get(F_FILE_PATH);
        if (StringUtils.isEmpty(filePath)) {
            return null;
        }
        File file = new File(filePath);
        return file.exists() && file.isFile() ? file : null;
    }

    private ExportTaskVO toVO(RMap<String, String> map) {
        if (map == null || !map.isExists()) {
            return null;
        }
        ExportTaskVO vo = new ExportTaskVO();
        vo.setTaskId(map.get(F_TASK_ID));
        vo.setStatus(map.get(F_STATUS));
        vo.setTotal(parseLong(map.get(F_TOTAL)));
        vo.setProcessed(parseLong(map.get(F_PROCESSED)));
        vo.setProgress(parseInt(map.get(F_PROGRESS)));
        vo.setFileName(map.get(F_FILE_NAME));
        vo.setErrorMsg(map.get(F_ERROR_MSG));
        vo.setCreateTime(parseLong(map.get(F_CREATE_TIME)));
        vo.setStartTime(parseLong(map.get(F_START_TIME)));
        vo.setFinishTime(parseLong(map.get(F_FINISH_TIME)));
        return vo;
    }

    private long parseLong(String s) {
        if (s == null || s.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private int parseInt(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
