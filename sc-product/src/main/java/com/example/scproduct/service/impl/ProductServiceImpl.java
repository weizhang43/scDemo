package com.example.scproduct.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Product;
import com.example.scproduct.es.ProductDescEsService;
import com.example.scproduct.mapper.ProductMapper;
import com.example.scproduct.service.ProductService;
import com.example.scproduct.vo.ProductExportVO;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RScript;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {
    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ProductDescEsService productDescEsService;

    /** 商品点赞数缓存：Redis Hash，field=商品ID，value=当前总点赞数（含未落库部分），作为权威值 */
    private static final String LIKE_COUNT_KEY = "product:like:count";
    /** 待落库商品ID集合：记录点赞数发生变化、需回写 DB 的商品 */
    private static final String LIKE_DIRTY_KEY = "product:like:dirty";

    /** 秒杀库存 key 前缀：seckill:stock:{pId}，String 类型 */
    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    /** 秒杀已购用户集合 key 前缀：seckill:bought:{pId}，Set 类型，用于一人一单 */
    private static final String SECKILL_BOUGHT_KEY = "seckill:bought:";

    /**
     * 秒杀预扣 Lua：原子完成 一人一单校验 + 余量判断 + 扣减 + 记录已购。
     * 返回：1 成功；0 售罄；-1 已参与；-2 库存未就绪。
     */
    private static final String SECKILL_LUA =
            "local stock = redis.call('get', KEYS[1]) " +
            "if stock == false then return -2 end " +
            "if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then return -1 end " +
            "if tonumber(stock) <= 0 then return 0 end " +
            "redis.call('decr', KEYS[1]) " +
            "redis.call('sadd', KEYS[2], ARGV[1]) " +
            "return 1";

    /** 秒杀补偿 Lua：库存 +1 并移除已购标记 */
    private static final String SECKILL_ROLLBACK_LUA =
            "redis.call('incr', KEYS[1]) " +
            "redis.call('srem', KEYS[2], ARGV[1]) " +
            "return 1";

    /**
     * 演示链路：插入一条固定垃圾袋商品。
     */
    @Override
    public ResponseDto<Product> addProduct() {
        Product product = new Product();
        product.setPName("垃圾袋");
        product.setPrice(200);
        product.setStock(10000);
        productMapper.insert(product);
        return ResponseDto.success(null);
    }

    /**
     * 按商品名关键字 + 价格区间分页查询，按价格倒序、ID 升序返回。
     */
    @Override
    public ResponseDto<Product> queryProduct(String key, int price, int pageNo, int pageSize) {
        Page<Product> page = new Page<>(pageNo,pageSize);
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<Product>()
                .select(Product::getPId, Product::getPrice, Product::getPName, Product::getStock)
                .like(Product::getPName,key)
                .gt(Product::getPrice,price)
                .orderByDesc(Product::getPrice)
                .orderByAsc(Product::getPId);
        page = productMapper.selectPage(page,queryWrapper);
        return ResponseDto.success(page);
    }

    /**
     * 多条件分页查询：复用 buildListWrapper（含 ES 模糊检索降级），并把 Redis 中实时点赞数合并回结果。
     */
    @Override
    public ResponseDto<Product> pageQuery(String pName,
                                         String proDesc,
                                         Date productionDateStart,
                                         Date productionDateEnd,
                                         String origin,
                                         Integer isExpired,
                                         int pageNo,
                                         int pageSize) {
        Page<Product> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<Product> queryWrapper = buildListWrapper(pName, proDesc, productionDateStart,
                productionDateEnd, origin, isExpired);
        page = productMapper.selectPage(page, queryWrapper);
        mergeLikeCountFromRedis(page.getRecords());
        return ResponseDto.success(page);
    }

    /**
     * 构建商品列表/导出查询 wrapper。
     * proDesc 非空时先查 ES 召回 pId 列表，再 IN 到 MySQL：
     *   - ES 命中为空 → IN (空集)，等价于无数据，避免全表 LIKE 扫描
     *   - ES 不可用  → 降级回 MySQL LIKE，保证功能可用（牺牲性能）
     */
    private LambdaQueryWrapper<Product> buildListWrapper(String pName,
                                                         String proDesc,
                                                         Date productionDateStart,
                                                         Date productionDateEnd,
                                                         String origin,
                                                         Integer isExpired) {
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<Product>()
                .like(pName != null && !pName.isEmpty(), Product::getPName, pName)
                .ge(productionDateStart != null, Product::getProductionDate, productionDateStart)
                .le(productionDateEnd != null, Product::getProductionDate, productionDateEnd)
                .eq(origin != null && !origin.isEmpty(), Product::getOrigin, origin)
                .eq(isExpired != null, Product::getIsExpired, isExpired)
                .orderByDesc(Product::getPId);

        boolean hasProDesc =  proDesc != null && !proDesc.trim().isEmpty();
        if (!hasProDesc) {
            return queryWrapper;
        }

        try {
            List<Integer> hitIds = productDescEsService.searchPIdsByDesc(proDesc);
            if (hitIds.isEmpty()) {
                // ES 命中为空：直接用不可能存在的 pId 让查询短路返回空，避免全表 LIKE
                queryWrapper.in(Product::getPId, -1);
            } else {
                queryWrapper.in(Product::getPId, hitIds);
            }
        } catch (Exception e) {
            log.warn("[pageQuery] es unavailable, fallback to mysql LIKE, proDesc='{}'", proDesc, e);
            queryWrapper.like(true, Product::getProDesc, proDesc);
        }
        return queryWrapper;
    }

    /** 用 Redis 中的权威点赞数覆盖 DB 查出的值，保证列表展示接近实时 */
    private void mergeLikeCountFromRedis(List<Product> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        try {
            Set<Integer> ids = records.stream().map(Product::getPId).collect(Collectors.toSet());
            Map<Integer, Long> cached = redissonClient.<Integer, Long>getMap(LIKE_COUNT_KEY).getAll(ids);
            if (cached.isEmpty()) {
                return;
            }
            for (Product p : records) {
                Long c = cached.get(p.getPId());
                if (c != null) {
                    p.setLikeCount(c.intValue());
                }
            }
        } catch (Exception e) {
            // Redis 不可用时降级为 DB 值展示
            log.warn("[mergeLikeCountFromRedis] redis unavailable, use db value", e);
        }
    }

    /**
     * 新增商品：根据生产日期+保质期自动计算 is_expired 字段后入库。
     */
    @Override
    public ResponseDto<Product> addOne(Product product) {
        refreshExpiredFlag(product);
        productMapper.insert(product);
        return ResponseDto.success(null);
    }

    /**
     * 商品点赞：Redis Hash 计数并标脏等待回写；首次点赞时以 DB 现值播种。
     * Redis 不可用时降级为直接 SQL 累加，保证功能可用。
     */
    @Override
    public ResponseDto<Product> like(Integer id) {
        if (id == null) {
            return ResponseDto.error("商品ID不能为空");
        }
        try {
            RMap<Integer, Long> countMap = redissonClient.getMap(LIKE_COUNT_KEY);
            // 首次点赞该商品时，用 DB 现值给 Redis 播种，之后 Redis 即为权威值
            if (!countMap.containsKey(id)) {
                Product db = getById(id);
                if (db == null) {
                    return ResponseDto.error("点赞失败：商品不存在");
                }
                countMap.putIfAbsent(id, db.getLikeCount() == null ? 0L : db.getLikeCount().longValue());
            }
            long newCount = countMap.addAndGet(id, 1L);
            redissonClient.getSet(LIKE_DIRTY_KEY).add(id);
            Product vo = new Product();
            vo.setPId(id);
            vo.setLikeCount((int) newCount);
            return ResponseDto.success(vo);
        } catch (Exception e) {
            // Redis 不可用时降级：直接原子写库，保证功能可用（牺牲削峰能力）
            log.warn("[like] redis unavailable, fallback to db, pId={}", id, e);
            boolean ok = update(new LambdaUpdateWrapper<Product>()
                    .eq(Product::getPId, id)
                    .setSql("like_count = COALESCE(like_count, 0) + 1"));
            if (!ok) {
                return ResponseDto.error("点赞失败：商品不存在");
            }
            return ResponseDto.success(getById(id));
        }
    }

    /**
     * 批量回写脏集合中商品的点赞数：先移出脏集合，幂等覆盖 DB 值；失败时重新标脏等下次重试。
     * @return 实际回写成功的商品数
     */
    @Override
    public int flushLikeCount() {
        RSet<Integer> dirtySet = redissonClient.getSet(LIKE_DIRTY_KEY);
        RMap<Integer, Long> countMap = redissonClient.getMap(LIKE_COUNT_KEY);
        Set<Integer> ids = new HashSet<>(dirtySet.readAll());
        if (ids.isEmpty()) {
            return 0;
        }
        int flushed = 0;
        for (Integer pId : ids) {
            Long count = countMap.get(pId);
            // 先移出脏集合；期间若有新点赞会重新加回，下次调度再落库
            dirtySet.remove(pId);
            if (count == null) {
                continue;
            }
            try {
                // 幂等回写：以 Redis 权威值覆盖 DB，重复执行结果一致，不会重复累加
                int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                        .eq(Product::getPId, pId)
                        .set(Product::getLikeCount, count.intValue()));
                if (rows > 0) {
                    flushed++;
                }
                // rows==0 说明商品已删除，丢弃该计数即可
            } catch (Exception e) {
                // 回写失败，重新标脏，等待下次调度重试
                dirtySet.add(pId);
                log.error("[flushLikeCount] write back failed, pId={}, count={}", pId, count, e);
            }
        }
        return flushed;
    }

    /**
     * 秒杀预扣库存：Redis 无库存时懒加载 DB 并校验是否可秒杀；以 Lua 脚本原子完成一人一单校验+扣减+已购记录。
     * @return code=200 预扣成功；非 200 为失败原因（已售罄/已参与/未就绪/商品不存在等）
     */
    @Override
    public ResponseDto<Product> seckillPreDeduct(Integer pId, Integer uId) {
        if (pId == null || uId == null) {
            return ResponseDto.error("参数不能为空");
        }
        String stockKey = SECKILL_STOCK_KEY + pId;
        String boughtKey = SECKILL_BOUGHT_KEY + pId;
        // 懒加载播种：Redis 无库存时从 DB 载入并校验是否可秒杀
        RBucket<String> stockBucket = redissonClient.getBucket(stockKey, StringCodec.INSTANCE);
        if (!stockBucket.isExists()) {
            Product db = getById(pId);
            if (db == null) {
                return ResponseDto.error("商品不可秒杀：商品不存在");
            }
            if (db.getIsExpired() != null && db.getIsExpired() == 1) {
                return ResponseDto.error("商品不可秒杀：已过期");
            }
            int stock = db.getStock() == null ? 0 : db.getStock();
            if (stock < 1) {
                return ResponseDto.error("已售罄");
            }
            // trySet 即 SETNX，并发下只有首个线程播种成功，其余直接进入 Lua
            stockBucket.trySet(String.valueOf(stock));
        }
        List<Object> keys = new ArrayList<>();
        keys.add(stockKey);
        keys.add(boughtKey);
        Long code = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE, SECKILL_LUA, RScript.ReturnType.INTEGER,
                keys, String.valueOf(uId));
        long r = code == null ? 0L : code;
        if (r == 1L) {
            return ResponseDto.success(null);
        }
        if (r == -1L) {
            return ResponseDto.error("您已参与过该商品秒杀");
        }
        if (r == -2L) {
            return ResponseDto.error("秒杀未就绪，请稍后重试");
        }
        return ResponseDto.error("已售罄");
    }

    @Override
    public ResponseDto<Product> rollbackSeckillStock(Integer pId, Integer uId) {
        if (pId == null || uId == null) {
            return ResponseDto.error("参数不能为空");
        }
        List<Object> keys = new ArrayList<>();
        keys.add(SECKILL_STOCK_KEY + pId);
        keys.add(SECKILL_BOUGHT_KEY + pId);
        redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE, SECKILL_ROLLBACK_LUA, RScript.ReturnType.INTEGER,
                keys, String.valueOf(uId));
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<Product> deductStock(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return ResponseDto.error("下单商品列表为空");
        }
        for (Product item : products) {
            Product db = productMapper.selectById(item.getPId());
            if (db == null) {
                return ResponseDto.error("商品不存在: id=" + item.getPId());
            }
            int need = item.getStock() == null ? 0 : item.getStock();
            if (db.getStock() == null || db.getStock() < need) {
                return ResponseDto.error("库存不足：" + db.getPName());
            }
            db.setStock(db.getStock() - need);
            productMapper.updateById(db);
        }
        return ResponseDto.success(null);
    }

    /**
     * 批量校验库存充足后一次性扣减库存。
     * 任一库存不足 → 全部不扣，返回失败。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseDto<Product> checkAndDeductStock(List<Product> items) {
        if (items == null || items.isEmpty()) {
            return ResponseDto.error("下单商品列表为空");
        }
        // 校验入参
        for (Product item : items) {
            Integer pId = item.getPId();
            int need = item.getStock() == null ? 0 : item.getStock();
            if (need <= 0) {
                return ResponseDto.error("下单数量必须大于0: pId=" + pId);
            }
        }
        // 对每个商品依次加分布式锁后扣减；任一失败抛错，@GlobalTransactional 回滚此前已扣的
        for (Product item : items) {
            Integer pId = item.getPId();
            int need = item.getStock();
            String lockKey = "lock:product:stock:" + pId;
            RLock lock = redissonClient.getLock(lockKey);
            boolean locked = false;
            try {
                locked = lock.tryLock(3, 30, TimeUnit.SECONDS);
                if (!locked) {
                    return ResponseDto.error("下单繁忙，请稍后重试: pId=" + pId);
                }
                // 锁内：先查库存快照（给业务返回友好提示）
                Product db = productMapper.selectById(pId);
                if (db == null) {
                    return ResponseDto.error("商品不存在: id=" + pId);
                }
                if (db.getStock() == null || db.getStock() < need) {
                    return ResponseDto.error("库存不足：" + db.getPName()
                            + "（剩余 " + (db.getStock() == null ? 0 : db.getStock()) + "）");
                }
                // 锁内条件 UPDATE：兜底防超卖，应对锁内意外并发/事务延迟
                int rows = productMapper.update(null,
                        new LambdaUpdateWrapper<Product>()
                                .eq(Product::getPId, pId)
                                .ge(Product::getStock, need)
                                .setSql("stock = stock - " + need));
                if (rows == 0) {
                    return ResponseDto.error("库存不足（并发）：pId=" + pId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ResponseDto.error("加锁中断: pId=" + pId);
            } finally {
                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
        return ResponseDto.success(null);
    }

    /** 根据生产日期+保质期计算 isExpired 字段 */
    private void refreshExpiredFlag(Product product) {
        if (product.getProductionDate() == null || product.getShelfLife() == null) {
            return;
        }
        long shelfMs = product.getShelfLife() * 24L * 60L * 60L * 1000L;
        long expireAt = product.getProductionDate().getTime() + shelfMs;
        boolean expired = System.currentTimeMillis() > expireAt;
        product.setIsExpired(expired ? 1 : 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markExpiredProducts() {
        // 查询所有未过期商品
        List<Product> notExpired = productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getIsExpired, 0)
        );
        if (notExpired.isEmpty()) {
            return 0;
        }
        // 过滤出实际已过期的：production_date + shelf_life 天 < 当前日期
        long now = System.currentTimeMillis();
        List<Integer> expiredIds = notExpired.stream()
                .filter(p -> p.getProductionDate() != null && p.getShelfLife() != null)
                .filter(p -> {
                    long shelfMs = p.getShelfLife() * 24L * 60L * 60L * 1000L;
                    return p.getProductionDate().getTime() + shelfMs < now;
                })
                .map(Product::getPId)
                .collect(Collectors.toList());
        if (expiredIds.isEmpty()) {
            return 0;
        }
        boolean ok = update(new LambdaUpdateWrapper<Product>()
                .in(Product::getPId, expiredIds)
                .set(Product::getIsExpired, 1));
        return ok ? expiredIds.size() : 0;
    }

    @Override
    public void export(String pName,
                       String proDesc,
                       Date productionDateStart,
                       Date productionDateEnd,
                       String origin,
                       Integer isExpired,
                       HttpServletResponse response) throws Exception {
        LambdaQueryWrapper<Product> queryWrapper = buildListWrapper(pName, proDesc, productionDateStart,
                productionDateEnd, origin, isExpired);
        List<Product> list = productMapper.selectList(queryWrapper);
        List<ProductExportVO> rows = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            rows.add(ProductExportVO.of(list.get(i), i + 1));
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("商品列表", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), ProductExportVO.class)
                .sheet("商品列表")
                .doWrite(rows);
    }

    @Override
    public long exportToPath(ProductExportQuery query,
                             String outputPath,
                             long maxRows,
                             int pageSize,
                             ProgressCallback progressCallback,
                             CancelChecker cancelChecker) throws Exception {
        // 1. 先 count 校验上限
        LambdaQueryWrapper<Product> baseWrapper = buildExportWrapper(query);
        long total = productMapper.selectCount(baseWrapper);
        if (total > maxRows) {
            throw new IllegalStateException("导出数据量 " + total + " 超过最大限制 " + maxRows + "，请缩小查询条件后重试");
        }
        if (total == 0) {
            // 空数据仍生成空表头文件
            try (FileOutputStream fos = new FileOutputStream(outputPath);
                 ExcelWriter writer = EasyExcel.write(fos, ProductExportVO.class).build()) {
                WriteSheet sheet = EasyExcel.writerSheet("商品列表").build();
                writer.write(new ArrayList<ProductExportVO>(), sheet);
            }
            progressCallback.onProgress(0, 0);
            return 0;
        }

        // 2. 分页查询 + 分页写入，复用 ExcelWriter
        long processed = 0;
        try (FileOutputStream fos = new FileOutputStream(outputPath);
             ExcelWriter writer = EasyExcel.write(fos, ProductExportVO.class).build()) {
            WriteSheet sheet = EasyExcel.writerSheet("商品列表").build();
            int pageNo = 1;
            while (true) {
                if (cancelChecker != null && cancelChecker.isCanceled()) {
                    throw new InterruptedException("导出任务已被取消");
                }
                Page<Product> page = new Page<>(pageNo, pageSize, false);
                Page<Product> result = productMapper.selectPage(page, buildExportWrapper(query));
                List<Product> records = result.getRecords();
                if (records == null || records.isEmpty()) {
                    break;
                }
                List<ProductExportVO> rows = new ArrayList<>(records.size());
                int base = (pageNo - 1) * pageSize;
                for (int i = 0; i < records.size(); i++) {
                    rows.add(ProductExportVO.of(records.get(i), base + i + 1));
                }
                writer.write(rows, sheet);
                processed += rows.size();
                if (progressCallback != null) {
                    progressCallback.onProgress(processed, total);
                }
                if (records.size() < pageSize) {
                    break;
                }
                pageNo++;
            }
        }
        return processed;
    }

    private LambdaQueryWrapper<Product> buildExportWrapper(ProductExportQuery q) {
        return buildListWrapper(q.getpName(), q.getProDesc(), q.getProductionDateStart(),
                q.getProductionDateEnd(), q.getOrigin(), q.getIsExpired());
    }
}
