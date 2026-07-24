package com.example.scproduct.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.curry.model.Product;
import com.example.scproduct.controller.FileController;
import com.example.scproduct.es.ProductDescEsService;
import com.example.scproduct.mapper.ProductMapper;
import com.example.scproduct.service.ProductJobService;
import com.example.scproduct.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import response.ResponseDto;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static response.ResponseDto.SUCCESS_CODE;

/**
 * 商品定时任务业务实现：AI 补描述 + ES 同步、ES 全量重建、商品图片绑定。
 * 由 sc-job 通过 Feign 触发（/product/job/*）。
 */
@Slf4j
@Service
public class ProductJobServiceImpl implements ProductJobService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductDescEsService esService;

    @Autowired
    private FileController fileController;

    @Value("${scdemo.chat.base-url:http://localhost:9000}")
    private String chatBaseUrl;

    @Value("${product.es.sync-page-size:500}")
    private int syncPageSize;

    @Value("${product.image.source-dir:D:/code/project/photo}")
    private String imageSourceDir;

    private static final int PAGE_SIZE = 100;
    private static final int CONCURRENCY = 8;
    private static final long AWAIT_TIMEOUT_MINUTES = 5;

    /**
     * 游标分页扫表，固定线程池并发为商品补 proDesc，写回 MySQL 并同步到 ES。
     * 单批并发等待最长 5 分钟，超时则记录并继续下一批。
     */
    @Override
    public String fillProDesc() {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        log.info("[fillProDesc] start, scanTime={}", now);
        long start = System.currentTimeMillis();

        long total = productMapper.selectCount(new LambdaQueryWrapper<>());
        long processed = 0;
        long updated = 0;
        long esSynced = 0;
        Integer lastPId = null;

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        try {
            while (true) {
                LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                        .orderByDesc(Product::getPId)
                        .last("LIMIT " + PAGE_SIZE);
                if (lastPId != null) {
                    wrapper.lt(Product::getPId, lastPId);
                }
                List<Product> page = productMapper.selectList(wrapper);
                if (CollectionUtils.isEmpty(page)) {
                    break;
                }

                List<CompletableFuture<Void>> futures = new ArrayList<>(page.size());
                for (Product product : page) {
                    futures.add(CompletableFuture.runAsync(() -> fillOneProDesc(product), executor));
                }
                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .get(AWAIT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.error("[fillProDesc] partial batch failure, continuing. lastPId={}", lastPId, e);
                }

                List<Product> changed = new ArrayList<>(page.size());
                for (Product p : page) {
                    if (p.getProDesc() != null) {
                        changed.add(p);
                    }
                }
                if (!changed.isEmpty()) {
                    productService.updateBatchById(changed);
                    updated += changed.size();
                    // 同步本批到 ES：ES 异常不阻断 AI 填充主流程，仅记录
                    try {
                        esSynced += esService.upsertBatch(changed);
                    } catch (Exception esErr) {
                        log.warn("[fillProDesc] es sync batch failed, size={}", changed.size(), esErr);
                    }
                }

                processed += page.size();
                lastPId = page.get(page.size() - 1).getPId();
                if (page.size() < PAGE_SIZE) {
                    break;
                }
            }

            String summary = String.format("total=%d, processed=%d, proDescUpdated=%d, esSynced=%d, costMs=%d",
                    total, processed, updated, esSynced, System.currentTimeMillis() - start);
            log.info("[fillProDesc] finish, {}", summary);
            return summary;
        } catch (Exception e) {
            log.error("[fillProDesc] error, processed={}, updated={}", processed, updated, e);
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 全量重建 ES 索引：分页扫表 bulk upsert。首次启用 ES 时手动调度一次。
     */
    @Override
    public String rebuildProDescIndex() {
        long start = System.currentTimeMillis();
        try {
            long indexed = 0;
            int pageNo = 1;
            while (true) {
                Page<Product> page = new Page<>(pageNo, syncPageSize, false);
                Page<Product> result = productMapper.selectPage(page,
                        new LambdaQueryWrapper<Product>().orderByAsc(Product::getPId));
                List<Product> records = result.getRecords();
                if (records == null || records.isEmpty()) {
                    break;
                }
                indexed += esService.upsertBatch(records);
                if (records.size() < syncPageSize) {
                    break;
                }
                pageNo++;
            }
            String summary = String.format("indexed=%d, costMs=%d", indexed, System.currentTimeMillis() - start);
            log.info("[rebuildProDescIndex] full rebuild finish, {}", summary);
            return summary;
        } catch (Exception e) {
            log.error("[rebuildProDescIndex] error", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 调用 chat 服务为单个商品生成描述：构造"请给商品 X 加一个 200 字左右的描述"作为 query，
     * 响应非空则回写到 product.proDesc，由调用方统一批量入库。
     */
    private void fillOneProDesc(Product product) {
        try {
            String name = product.getPName();
            if (name == null || name.isEmpty()) {
                log.warn("[fillProDesc] skip product with null name, pId={}", product.getPId());
                return;
            }
            String url = UriComponentsBuilder.fromHttpUrl(chatBaseUrl)
                    .path("chat")
                    .queryParam("message", "请给商品\"" + name + "\"加一个200字左右的描述")
                    .encode(StandardCharsets.UTF_8)
                    .toUriString();
            String proDesc = restTemplate.getForObject(url, String.class);
            if (proDesc != null && !proDesc.isEmpty()) {
                product.setProDesc(proDesc);
            }
        } catch (Exception e) {
            log.error("[fillProDesc] fillOneProDesc fail, pId={}, name={}",
                    product.getPId(), product.getPName(), e);
        }
    }

    @Override
    public String dealProductImage() {
        long start = System.currentTimeMillis();
        log.info("[dealProductImage] start");

        List<File> fileList = getFileList();
        if (fileList.isEmpty()) {
            log.warn("[dealProductImage] image source dir empty, skip. dir={}", imageSourceDir);
            return "image source dir empty, skip. dir=" + imageSourceDir;
        }
        // 预读全部图片字节缓存到内存，避免每次上传都重新读盘
        List<MultipartFile> mockFiles = loadMockFiles(fileList);
        if (mockFiles.isEmpty()) {
            log.warn("[dealProductImage] no readable image, skip. dir={}", imageSourceDir);
            return "no readable image, skip. dir=" + imageSourceDir;
        }

        long total = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .isNull(Product::getImageUrl));
        long updated = 0;
        long failed = 0;
        Integer lastPId = null;
        Random rand = new Random();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);

        try {
            while (true) {
                LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                        .isNull(Product::getImageUrl)
                        .orderByDesc(Product::getPId)
                        .last("LIMIT " + syncPageSize);
                if (lastPId != null) {
                    wrapper.lt(Product::getPId, lastPId);
                }
                List<Product> page = productMapper.selectList(wrapper);
                if (CollectionUtils.isEmpty(page)) {
                    break;
                }

                List<CompletableFuture<Boolean>> futures = new ArrayList<>(page.size());
                for (Product product : page) {
                    futures.add(CompletableFuture.supplyAsync(() -> uploadAndBind(product, mockFiles, rand), executor));
                }

                int batchUpdated = 0;
                int batchFailed = 0;
                List<Product> changed = new ArrayList<>(page.size());
                for (int i = 0; i < futures.size(); i++) {
                    try {
                        if (Boolean.TRUE.equals(futures.get(i).get(AWAIT_TIMEOUT_MINUTES, TimeUnit.MINUTES))) {
                            changed.add(page.get(i));
                            batchUpdated++;
                        } else {
                            batchFailed++;
                        }
                    } catch (Exception e) {
                        batchFailed++;
                        log.error("[dealProductImage] task fail, pId={}", page.get(i).getPId(), e);
                    }
                }

                if (!changed.isEmpty()) {
                    productService.updateBatchById(changed);
                }
                updated += batchUpdated;
                failed += batchFailed;

                // 关键：游标必须推进，无论本批是否有失败，否则失败的行永远卡在第 1 页
                lastPId = page.get(page.size() - 1).getPId();
                if (page.size() < syncPageSize) {
                    break;
                }
            }

            String summary = String.format("pending=%d, updated=%d, failed=%d, costMs=%d",
                    total, updated, failed, System.currentTimeMillis() - start);
            log.info("[dealProductImage] finish, {}", summary);
            return summary;
        } catch (Exception e) {
            log.error("[dealProductImage] error, updated={}, failed={}", updated, failed, e);
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 随机选一张图上传，写回 product.imageUrl。
     * 返回 true 表示成功；返回 false 表示跳过（图片读取失败/上传接口返回非 200）。
     */
    private boolean uploadAndBind(Product product, List<MultipartFile> mockFiles, Random rand) {
        try {
            MultipartFile file = mockFiles.get(rand.nextInt(mockFiles.size()));
            ResponseDto<String> resp = fileController.upload(file);
            if (resp != null && SUCCESS_CODE.equals(resp.getCode()) && resp.getDaoResult() != null) {
                product.setImageUrl(String.valueOf(resp.getDaoResult()));
                return true;
            }
            log.warn("[dealProductImage] upload resp invalid, pId={}, resp={}", product.getPId(), resp);
        } catch (Exception e) {
            log.error("[dealProductImage] upload fail, pId={}", product.getPId(), e);
        }
        return false;
    }

    /**
     * 把目录下的所有图片一次性加载为 MockMultipartFile，避免反复读盘。
     * 仅保留常见图片扩展名，跳过非图片/不可读文件。
     */
    private List<MultipartFile> loadMockFiles(List<File> files) {
        List<MultipartFile> result = new ArrayList<>(files.size());
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (!name.matches(".*\\.(png|jpg|jpeg|gif|webp)")) {
                continue;
            }
            try {
                byte[] bytes = FileUtils.readFileToByteArray(file);
                result.add(new MockMultipartFile("file", file.getName(), null, bytes));
            } catch (IOException e) {
                log.warn("[dealProductImage] skip unreadable file: {}", file.getAbsolutePath(), e);
            }
        }
        return result;
    }

    /**
     * 获取文件夹下所有的附件
     */
    private List<File> getFileList() {
        File dir = new File(imageSourceDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return new ArrayList<>();
        }
        Collection<File> files = FileUtils.listFiles(dir, null, true);
        return new ArrayList<>(files);
    }
}
