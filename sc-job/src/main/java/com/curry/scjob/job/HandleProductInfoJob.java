package com.curry.scjob.job;

import com.curry.scjob.service.ProductFeignService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import response.ResponseDto;

import java.text.SimpleDateFormat;
import java.util.Date;

import static response.ResponseDto.SUCCESS_CODE;

/**
 * 商品信息处理任务集合：AI 补描述 + ES 同步、ES 全量重建、商品图片绑定。
 * 具体业务逻辑在 sc-product（/product/job/*），本类仅做 XXL-Job 调度触发。
 */
@Slf4j
@Component
public class HandleProductInfoJob {

    @Autowired
    private ProductFeignService productFeignService;

    /**
     * 调度名称：handleProDescJob
     * 逻辑：触发 sc-product 分页扫表，并发调用 chat 服务为 proDesc 为空的商品生成描述，
     *       批量更新 MySQL 并同步到 ES。
     */
    @XxlJob("handleProDescJob")
    public void execute() {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        log.info("[handleProDescJob] start, scanTime={}", now);
        long start = System.currentTimeMillis();
        try {
            ResponseDto<String> resp = productFeignService.fillProDesc();
            if (resp == null || !SUCCESS_CODE.equals(resp.getCode())) {
                throw new RuntimeException("sc-product fillProDesc fail, resp=" + resp);
            }
            log.info("[handleProDescJob] finish, {} costMs={}",
                    resp.getDaoResult(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[handleProDescJob] error", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 调度名称：rebuildProDescIndexJob
     * 逻辑：触发 sc-product 全量重建 ES 索引。首次启用 ES 时手动调度一次。
     */
    @XxlJob("rebuildProDescIndexJob")
    public void rebuildAll() {
        long start = System.currentTimeMillis();
        try {
            ResponseDto<String> resp = productFeignService.rebuildProDescIndex();
            if (resp == null || !SUCCESS_CODE.equals(resp.getCode())) {
                throw new RuntimeException("sc-product rebuildProDescIndex fail, resp=" + resp);
            }
            log.info("[rebuildProDescIndexJob] finish, {} costMs={}",
                    resp.getDaoResult(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[rebuildProDescIndexJob] error", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 调度名称：dealProductImage
     * 逻辑：触发 sc-product 为 imageUrl 为空的商品随机绑定本地目录中的图片。
     */
    @XxlJob("dealProductImage")
    public void dealProductImage() {
        long start = System.currentTimeMillis();
        log.info("[dealProductImage] start");
        try {
            ResponseDto<String> resp = productFeignService.dealProductImage();
            if (resp == null || !SUCCESS_CODE.equals(resp.getCode())) {
                throw new RuntimeException("sc-product dealProductImage fail, resp=" + resp);
            }
            log.info("[dealProductImage] finish, {} costMs={}",
                    resp.getDaoResult(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[dealProductImage] error", e);
            throw new RuntimeException(e);
        }
    }
}
