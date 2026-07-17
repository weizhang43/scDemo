package com.example.scproduct.service.impl;

import com.example.scproduct.service.ExportTaskService;
import com.example.scproduct.service.ProductService;
import com.example.scproduct.vo.ExportTaskStatus;
import com.example.scproduct.vo.ExportTaskVO;
import org.redisson.api.RBucket;
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

    private static final Logger log = LoggerFactory.getLogger(ExportTaskServiceImpl.class);
    private static final String KEY_PREFIX = "export:product:task:";
    private static final long TTL_SECONDS = 3600L;
    private static final long FINAL_TTL_SECONDS = 1800L;

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
        map.put("taskId", taskId);
        map.put("status", ExportTaskStatus.PENDING.name());
        map.put("total", "0");
        map.put("processed", "0");
        map.put("progress", "0");
        map.put("fileName", "商品列表_" + taskId + ".xlsx");
        map.put("errorMsg", "");
        map.put("createTime", String.valueOf(now));
        map.put("startTime", "0");
        map.put("finishTime", "0");
        map.expire(TTL_SECONDS, TimeUnit.SECONDS);

        ExportTaskVO vo = toVO(map);
        try {
            exportExecutor.execute(() -> runExport(taskId, query));
        } catch (RejectedExecutionException e) {
            map.put("status", ExportTaskStatus.FAILED.name());
            map.put("errorMsg", "导出任务繁忙，请稍后重试");
            map.put("finishTime", String.valueOf(System.currentTimeMillis()));
            map.expire(FINAL_TTL_SECONDS, TimeUnit.SECONDS);
            vo.setStatus(ExportTaskStatus.FAILED.name());
            vo.setErrorMsg("导出任务繁忙，请稍后重试");
            return vo;
        }
        return vo;
    }

    private void runExport(String taskId, ProductService.ProductExportQuery query) {
        String key = KEY_PREFIX + taskId;
        RMap<String, String> map = redissonClient.getMap(key);
        long now = System.currentTimeMillis();
        map.put("status", ExportTaskStatus.RUNNING.name());
        map.put("startTime", String.valueOf(now));

        String dirPath = new File(exportDir).getAbsolutePath();
        File dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            markFailed(map, "无法创建导出目录: " + dirPath);
            return;
        }
        String fileName = map.get("fileName");
        File file = new File(dir, fileName);
        String outputPath = file.getAbsolutePath();

        try {
            long[] processedHolder = new long[]{0};
            long[] totalHolder = new long[]{0};
            long written = productService.exportToPath(
                    query,
                    outputPath,
                    maxRows,
                    pageSize,
                    (processed, total) -> {
                        processedHolder[0] = processed;
                        totalHolder[0] = total;
                        map.put("processed", String.valueOf(processed));
                        map.put("total", String.valueOf(total));
                        int progress = total <= 0 ? 0 : (int) Math.min(100, processed * 100 / total);
                        map.put("progress", String.valueOf(progress));
                    },
                    () -> ExportTaskStatus.CANCELED.name().equals(map.get("status"))
            );
            if (ExportTaskStatus.CANCELED.name().equals(map.get("status"))) {
                file.delete();
                return;
            }
            map.put("processed", String.valueOf(written));
            map.put("progress", "100");
            map.put("filePath", outputPath);
            map.put("status", ExportTaskStatus.SUCCESS.name());
            map.put("finishTime", String.valueOf(System.currentTimeMillis()));
            map.expire(FINAL_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            file.delete();
            if (!ExportTaskStatus.CANCELED.name().equals(map.get("status"))) {
                map.put("status", ExportTaskStatus.CANCELED.name());
                map.put("finishTime", String.valueOf(System.currentTimeMillis()));
            }
            map.expire(FINAL_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("导出任务失败 taskId={}", taskId, e);
            file.delete();
            markFailed(map, e.getMessage());
        }
    }

    private void markFailed(RMap<String, String> map, String msg) {
        map.put("status", ExportTaskStatus.FAILED.name());
        map.put("errorMsg", msg == null ? "导出失败" : msg);
        map.put("finishTime", String.valueOf(System.currentTimeMillis()));
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
        String current = map.get("status");
        // 终态任务不可取消
        if (ExportTaskStatus.SUCCESS.name().equals(current)
                || ExportTaskStatus.FAILED.name().equals(current)
                || ExportTaskStatus.CANCELED.name().equals(current)) {
            return toVO(map);
        }
        map.put("status", ExportTaskStatus.CANCELED.name());
        return toVO(map);
    }

    @Override
    public boolean download(String taskId, HttpServletResponse response) throws Exception {
        RMap<String, String> map = redissonClient.getMap(KEY_PREFIX + taskId);
        if (!map.isExists()) {
            return false;
        }
        String status = map.get("status");
        if (!ExportTaskStatus.SUCCESS.name().equals(status)) {
            return false;
        }
        String filePath = map.get("filePath");
        if (StringUtils.isEmpty(filePath)) {
            return false;
        }
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return false;
        }
        String fileName = map.get("fileName");
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
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) {
                os.write(buf, 0, n);
            }
            os.flush();
        }
        return true;
    }

    private ExportTaskVO toVO(RMap<String, String> map) {
        if (map == null || !map.isExists()) {
            return null;
        }
        ExportTaskVO vo = new ExportTaskVO();
        vo.setTaskId(map.get("taskId"));
        vo.setStatus(map.get("status"));
        vo.setTotal(parseLong(map.get("total")));
        vo.setProcessed(parseLong(map.get("processed")));
        vo.setProgress(parseInt(map.get("progress")));
        vo.setFileName(map.get("fileName"));
        vo.setErrorMsg(map.get("errorMsg"));
        vo.setCreateTime(parseLong(map.get("createTime")));
        vo.setStartTime(parseLong(map.get("startTime")));
        vo.setFinishTime(parseLong(map.get("finishTime")));
        return vo;
    }

    private long parseLong(String s) {
        if (s == null || s.isEmpty()) return 0L;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0L; }
    }

    private int parseInt(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
