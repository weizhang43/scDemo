package com.example.scuser;

import com.curry.model.OperationLog;
import com.example.scuser.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class OperationLogDataGenTest {

    private static final int TOTAL = 100_000;
    private static final int BATCH_SIZE = 1000;

    private static final String[] MODULES = {"用户管理", "订单管理", "商品管理", "系统设置", "权限管理", "报表中心"};
    private static final String[] OP_TYPES = {"QUERY", "ADD", "UPDATE", "DELETE", "LOGIN", "EXPORT", "OTHER"};
    private static final String[] REQUEST_METHODS = {"GET", "POST", "PUT", "DELETE"};
    private static final String[] USER_NAMES = {"admin", "zhangsan", "lisi", "wangwu", "zhaoliu", "curry", "tester"};
    private static final String[] URIS = {"/user/list", "/user/add", "/order/query", "/order/update",
            "/goods/delete", "/sys/config", "/auth/login", "/report/export"};

    @Autowired
    private OperationLogService operationLogService;

    @Test
    public void insertRandomLogs() {
        long start = System.currentTimeMillis();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<OperationLog> batch = new ArrayList<>(BATCH_SIZE);
        for (int i = 1; i <= TOTAL; i++) {
            batch.add(buildRandomLog(random));
            if (batch.size() == BATCH_SIZE) {
                operationLogService.saveBatch(batch, BATCH_SIZE);
                batch.clear();
                if (i % 10_000 == 0) {
                    System.out.println("已插入 " + i + " 条");
                }
            }
        }
        if (!batch.isEmpty()) {
            operationLogService.saveBatch(batch, BATCH_SIZE);
        }

        System.out.println("插入 " + TOTAL + " 条完成，耗时 " + (System.currentTimeMillis() - start) + " ms");
    }

    private OperationLog buildRandomLog(ThreadLocalRandom random) {
        OperationLog log = new OperationLog();
        int userIndex = random.nextInt(USER_NAMES.length);
        log.setUId(userIndex + 1);
        log.setUName(USER_NAMES[userIndex]);
        log.setModule(MODULES[random.nextInt(MODULES.length)]);
        String opType = OP_TYPES[random.nextInt(OP_TYPES.length)];
        log.setOpType(opType);
        log.setDescription(log.getModule() + "-" + opType + " 随机测试数据");
        log.setMethod("com.example.scuser.controller.TestController.method" + random.nextInt(20));
        log.setRequestUri(URIS[random.nextInt(URIS.length)]);
        log.setRequestMethod(REQUEST_METHODS[random.nextInt(REQUEST_METHODS.length)]);
        log.setRequestParams("{\"id\":" + random.nextInt(100000) + ",\"pageNum\":" + random.nextInt(1, 100) + "}");
        log.setResponseSummary("{\"code\":200,\"msg\":\"success\"}");
        log.setIp(random.nextInt(1, 255) + "." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255));
        log.setCostMs(random.nextLong(1, 3000));
        int status = random.nextInt(100) < 95 ? 1 : 0;
        log.setStatus(status);
        log.setErrorMsg(status == 0 ? "java.lang.RuntimeException: 模拟随机异常" : null);
        // 随机分布在最近 90 天内
        long offsetMs = random.nextLong(TimeUnit.DAYS.toMillis(90));
        log.setCreateTime(new Date(System.currentTimeMillis() - offsetMs));
        return log;
    }
}
