package com.example.scproduct.job;

import com.example.scproduct.service.ProductService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 商品过期处理任务
 * 调度名称：handleProductExpiredJob
 * 逻辑：扫描所有 is_expired=0 的商品，若 production_date + shelf_life 天 < 当前日期，则将 is_expired 改为 1。
 */
@Slf4j
@Component
public class HandleProductExpiredJobHandler {

    @Autowired
    private ProductService productService;

    @XxlJob("handleProductExpiredJob")
    public void execute() {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        log.info("[handleProductExpiredJob] start, scanTime={}", now);
        long start = System.currentTimeMillis();
        try {
            int count = productService.markExpiredProducts();
            log.info("[handleProductExpiredJob] finish, markedExpired={} costMs={}",
                    count, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[handleProductExpiredJob] error", e);
            throw new RuntimeException(e);
        }
    }
}
