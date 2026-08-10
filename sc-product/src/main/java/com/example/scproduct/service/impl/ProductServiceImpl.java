package com.example.scproduct.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Product;
import com.example.scproduct.auth.AudienceScope;
import com.example.scproduct.es.ProductDescEsService;
import com.example.scproduct.mapper.CategoryMapper;
import com.example.scproduct.mapper.ProductLikeMapper;
import com.example.scproduct.mapper.ProductMapper;
import com.example.scproduct.service.ProductService;
import com.example.scproduct.service.PromotionService;
import com.example.scproduct.service.SeckillActivityService;
import com.example.scproduct.vo.ProductExportVO;
import com.example.scproduct.vo.ProductQuery;
import com.example.scproduct.vo.ProductTypeCountVO;
import exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {
    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductLikeMapper productLikeMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ProductDescEsService productDescEsService;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private SeckillActivityService seckillActivityService;

    /**
     * 商品点赞数缓存：Redis Hash，field=商品ID，value=当前总点赞数。
     * 只服务于写侧削峰（攒批回写 t_product.like_count 快照列），读侧一律以 t_product_like 的行数为准。
     */
    private static final String LIKE_COUNT_KEY = "product:like:count";
    /** 待落库商品ID集合：记录点赞数发生变化、需回写 DB 的商品 */
    private static final String LIKE_DIRTY_KEY = "product:like:dirty";

    /** 上架状态：1-上架 0-下架 */
    private static final int STATUS_ON_SALE = 1;
    private static final int STATUS_OFF_SHELF = 0;

    /** 即将过期预警窗口（月） */
    private static final int EXPIRING_MONTHS_AHEAD = 3;
    /** 库存分布式锁：获取等待时长（秒） */
    private static final int STOCK_LOCK_WAIT_SECONDS = 3;
    /** 库存分布式锁：持有租期（秒），超时自动释放防死锁 */
    private static final int STOCK_LOCK_LEASE_SECONDS = 30;
    /** 一天的毫秒数 */
    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;
    /** 导出分页批次大小 */
    private static final int EXPORT_PAGE_SIZE = 1000;
    /** 导出 Excel 的文件名与工作表名 */
    private static final String EXPORT_SHEET_NAME = "商品列表";

    @Override
    public ResponseDto<ProductTypeCountVO> countByType(AudienceScope scope) {
        return ResponseDto.success(productMapper.selectCountByType(scope.getMerchantId()));
    }

    @Override
    public ResponseDto<Product> listExpiringSoon(AudienceScope scope) {
        return ResponseDto.success(productMapper.selectExpiringWithin(EXPIRING_MONTHS_AHEAD,
                scope.getMerchantId(), scope.isOnSaleOnly()));
    }

    @Override
    public ResponseDto<Product> listLowStock(int threshold, AudienceScope scope) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .lt(Product::getStock, threshold)
                .orderByAsc(Product::getStock);
        applyScope(wrapper, scope);
        return ResponseDto.success(productMapper.selectList(wrapper));
    }

    /**
     * 好评榜。点赞数直接由 selectPageWithStats 联 t_product_like 现取，
     * 排序与展示用的是同一个精确值，不再需要「过量取候选 + 内存重排」那套绕开快照列滞后的补偿逻辑。
     */
    @Override
    public ResponseDto<Product> listLikeRank(int limit, AudienceScope scope) {
        Page<Product> page = new Page<>(1, limit);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        applyScope(wrapper, scope);
        List<Product> records = productMapper.selectPageWithStats(page, wrapper, "likes").getRecords();
        promotionService.fillEffectivePrice(records);
        return ResponseDto.success(records);
    }

    @Override
    public ResponseDto<Product> listNewest(int limit, AudienceScope scope) {
        Page<Product> page = new Page<>(1, limit);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .orderByDesc(Product::getPId);
        applyScope(wrapper, scope);
        List<Product> records = productMapper.selectPage(page, wrapper).getRecords();
        promotionService.fillEffectivePrice(records);
        return ResponseDto.success(records);
    }

    @Override
    public Product getVisibleById(Integer id, AudienceScope scope) {
        if (id == null) {
            return null;
        }
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getPId, id);
        applyScope(wrapper, scope);
        Product product = productMapper.selectOne(wrapper);
        if (product != null) {
            promotionService.fillEffectivePrice(Collections.singletonList(product));
            // 详情页走的是 MP 的单表查询，拿到的 like_count 是快照列，这里用明细表的精确值覆盖
            product.setLikeCount((int) productLikeMapper.countByPId(id));
            if (product.getCategoryId() != null) {
                com.example.scproduct.entity.Category category = categoryMapper.selectById(product.getCategoryId());
                product.setCategoryName(category == null ? null : category.getName());
            }
        }
        return product;
    }

    @Override
    public List<Product> listSellableByIds(List<Integer> ids) {
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .in(Product::getPId, ids)
                .eq(Product::getStatus, STATUS_ON_SALE));
        promotionService.fillEffectivePrice(products);
        return products;
    }

    /**
     * 多条件分页查询：复用 buildListWrapper（含 ES 模糊检索降级）。
     * status 非空时再按上下架过滤（商家列表的在售 / 下架 tab）。
     * 无论是否指定 sortBy 都走 selectPageWithStats，保证卡片上的成交数 / 评价数 / 点赞数在默认排序下也有值。
     */
    @Override
    public ResponseDto<Product> pageQuery(ProductQuery query, AudienceScope scope) {
        Page<Product> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<Product> queryWrapper = buildListWrapper(query.getpName(), query.getProDesc(),
                query.getProductionDateStart(), query.getProductionDateEnd(),
                query.getOrigin(), query.getIsExpired(), scope);
        // status 只加在列表查询上，不进 buildListWrapper：导出沿用旧的「不限上下架」语义
        queryWrapper.eq(query.getStatus() != null, Product::getStatus, query.getStatus());
        if (query.getCategoryId() != null) {
            // 一级分类展开为「自身 + 子分类」：挂在二级分类上的商品也要被一级分类筛中
            List<Integer> ids = new ArrayList<>(categoryMapper.selectChildIds(query.getCategoryId()));
            ids.add(query.getCategoryId());
            queryWrapper.in(Product::getCategoryId, ids);
        }
        IPage<Product> result = productMapper.selectPageWithStats(page, queryWrapper, query.getSortBy());
        promotionService.fillEffectivePrice(result.getRecords());
        return ResponseDto.success(result);
    }

    /**
     * 按可见范围给查询 wrapper 追加过滤条件。内部调用与管理员不加任何条件。
     */
    private void applyScope(LambdaQueryWrapper<Product> wrapper, AudienceScope scope) {
        if (scope == null || scope.isUnrestricted()) {
            return;
        }
        if (scope.isOnSaleOnly()) {
            wrapper.eq(Product::getStatus, STATUS_ON_SALE);
        }
        Integer merchantId = scope.getMerchantId();
        if (merchantId != null) {
            // merchant_id IS NULL 是存量公共商品，任何商家可见可管
            wrapper.and(w -> w.eq(Product::getMerchantId, merchantId).or().isNull(Product::getMerchantId));
        }
    }

    /**
     * 构建商品列表/导出查询 wrapper（不含排序）。
     * 排序由调用方决定：pageQuery 走 selectPageWithStats 的 choose 分支，两个导出各自显式按 p_id 倒序。
     * 这里不能带 ORDER BY —— wrapper 会被 ${ew.customSqlSegment} 原样拼进自定义 SQL，与后面的 ORDER BY 撞车。
     * proDesc 非空时先查 ES 召回 pId 列表，再 IN 到 MySQL：
     *   - ES 命中为空 → IN (空集)，等价于无数据，避免全表 LIKE 扫描
     *   - ES 不可用  → 降级回 MySQL LIKE，保证功能可用（牺牲性能）
     */
    private LambdaQueryWrapper<Product> buildListWrapper(String pName,
                                                         String proDesc,
                                                         Date productionDateStart,
                                                         Date productionDateEnd,
                                                         String origin,
                                                         Integer isExpired,
                                                         AudienceScope scope) {
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<Product>()
                .like(pName != null && !pName.isEmpty(), Product::getPName, pName)
                .ge(productionDateStart != null, Product::getProductionDate, productionDateStart)
                .le(productionDateEnd != null, Product::getProductionDate, productionDateEnd)
                .eq(origin != null && !origin.isEmpty(), Product::getOrigin, origin)
                .eq(isExpired != null, Product::getIsExpired, isExpired);
        applyScope(queryWrapper, scope);

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

    /**
     * 新增商品：根据生产日期+保质期自动计算 is_expired 字段后入库。
     * 商家创建的商品盖上其 u_id 作为归属；管理员/内部调用创建的留 NULL 作公共商品。
     */
    @Override
    public ResponseDto<Product> addOne(Product product, AudienceScope scope) {
        refreshExpiredFlag(product);
        product.setMerchantId(scope.getMerchantId());
        product.setStatus(STATUS_ON_SALE);
        productMapper.insert(product);
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<Product> updateOne(Product product, AudienceScope scope) {
        if (product == null || product.getPId() == null) {
            throw new BusinessException("商品ID不能为空");
        }
        Product db = productMapper.selectById(product.getPId());
        if (db == null) {
            throw new BusinessException("商品不存在");
        }
        if (!scope.canManage(db.getMerchantId())) {
            throw new BusinessException("无权修改该商品");
        }
        // 归属与上下架状态不走通用编辑：前者不可转让，后者有独立接口
        product.setMerchantId(null);
        product.setStatus(null);
        return productMapper.updateById(product) > 0
                ? ResponseDto.success(null)
                : ResponseDto.error("修改商品失败");
    }

    @Override
    public ResponseDto<Product> removeOne(Integer id, AudienceScope scope) {
        if (id == null) {
            throw new BusinessException("商品ID不能为空");
        }
        Product db = productMapper.selectById(id);
        if (db == null) {
            throw new BusinessException("商品不存在");
        }
        if (!scope.canManage(db.getMerchantId())) {
            throw new BusinessException("无权删除该商品");
        }
        return productMapper.deleteById(id) > 0
                ? ResponseDto.success(null)
                : ResponseDto.error("删除商品失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseDto<Product> setShelfStatus(Integer id, Integer status, AudienceScope scope) {
        if (id == null || status == null || (status != 0 && status != STATUS_ON_SALE)) {
            throw new BusinessException("参数非法：status 只能为 0 或 1");
        }
        Product db = productMapper.selectById(id);
        if (db == null) {
            throw new BusinessException("商品不存在");
        }
        if (!scope.canManage(db.getMerchantId())) {
            throw new BusinessException("无权操作该商品");
        }
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getPId, id)
                .set(Product::getStatus, status));
        if (status == 0) {
            // 下架即结束未完结的秒杀：活动还挂着而商品顾客侧已经看不到，秒到手也下不了单
            int ended = seckillActivityService.endByProduct(id);
            if (ended > 0) {
                log.info("[setShelfStatus] pId={} off shelf, ended {} seckill activities", id, ended);
            }
        }
        return ResponseDto.success(null);
    }

    /**
     * 商品点赞：一人一商品只能点一次，去重靠 t_product_like 的唯一索引。
     * 点赞数以明细表行数为准（展示侧也读它），Redis 只承担把 t_product.like_count 快照列
     * 攒批回写的削峰职责，读侧不再依赖它，所以这里的 Redis 异常只记日志、不影响点赞成败。
     */
    @Override
    public ResponseDto<Product> like(Integer id, Integer uId) {
        validateLikeRequest(id, uId);
        // 去重网关：插不进去说明已点过，直接返回，绝不落到下面的计数逻辑
        if (productLikeMapper.insertIgnore(uId, id) == 0) {
            return ResponseDto.error("您已点赞过该商品");
        }
        long total = productLikeMapper.countByPId(id);
        try {
            // 用明细表的精确值覆盖而非自增：Redis 丢过 key 或快照列曾偏离时，这一步顺带把它拉回正确值
            redissonClient.<Integer, Long>getMap(LIKE_COUNT_KEY).put(id, total);
            redissonClient.getSet(LIKE_DIRTY_KEY).add(id);
        } catch (Exception e) {
            log.warn("[like] redis unavailable, skip write-back queue, pId={}", id, e);
        }
        Product vo = new Product();
        vo.setPId(id);
        vo.setLikeCount((int) total);
        return ResponseDto.success(vo);
    }

    /**
     * 校验点赞请求：商品ID、登录态与商品可见性，不满足抛业务异常。
     */
    private void validateLikeRequest(Integer id, Integer uId) {
        if (id == null) {
            throw new BusinessException("商品ID不能为空");
        }
        if (uId == null) {
            throw new BusinessException("未登录");
        }
        if (getVisibleById(id, AudienceScope.customer()) == null) {
            throw new BusinessException("点赞失败：商品不存在或已下架");
        }
    }

    @Override
    public ResponseDto<Integer> listMyLikedPIds(Integer uId, List<Integer> pIds) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        if (pIds == null || pIds.isEmpty()) {
            return ResponseDto.success(Collections.emptyList());
        }
        return ResponseDto.success(productLikeMapper.selectLikedPIds(uId, pIds));
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
     * @deprecated 旧扣减入口，历史实现为无锁的查-改-写，并发下会超卖。
     * 现委托给 {@link #checkAndDeductStock}，仅为兼容既有 /product/deductStock 端点保留。
     */
    @Deprecated
    @Override
    public ResponseDto<Product> deductStock(List<Product> products) {
        return checkAndDeductStock(products);
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
            int need = item.getStock() == null ? 0 : item.getStock();
            if (need <= 0) {
                throw new BusinessException("下单数量必须大于0: pId=" + item.getPId());
            }
        }
        // 对每个商品依次加分布式锁后扣减。
        // 扣减过程中的任何失败都必须抛 BusinessException 而不是 return error：
        // return 属于正常返回，本地 @Transactional 会提交此前已扣商品，造成"部分扣减"；
        // 抛异常才能触发本地回滚（以及上游 Seata @GlobalTransactional 的全局回滚）。
        // 异常由 GlobalExceptionHandler 转成 ResponseDto.error，Feign 调用方语义不变。
        for (Product item : items) {
            deductOneWithLock(item.getPId(), item.getStock());
        }
        return ResponseDto.success(null);
    }

    /**
     * 加分布式锁后扣减单个商品库存，商品不存在/库存不足/加锁失败抛业务异常触发回滚。
     */
    private void deductOneWithLock(Integer pId, int need) {
        RLock lock = redissonClient.getLock("lock:product:stock:" + pId);
        boolean locked = false;
        try {
            locked = lock.tryLock(STOCK_LOCK_WAIT_SECONDS, STOCK_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("下单繁忙，请稍后重试: pId=" + pId);
            }
            deductStockInLock(pId, need);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("加锁中断: pId=" + pId, e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 锁内扣减：先查库存快照给出友好提示，再用条件 UPDATE 兜底防超卖。
     */
    private void deductStockInLock(Integer pId, int need) {
        // 锁内：先查库存快照（给业务返回友好提示）
        Product db = productMapper.selectById(pId);
        if (db == null) {
            throw new BusinessException("商品不存在: id=" + pId);
        }
        if (db.getStock() == null || db.getStock() < need) {
            throw new BusinessException("库存不足：" + db.getPName()
                    + "（剩余 " + (db.getStock() == null ? 0 : db.getStock()) + "）");
        }
        // 锁内条件 UPDATE：兜底防超卖，应对锁内意外并发/事务延迟。
        // setSql 拼接的 need 为 int 基本类型且已在入口校验 > 0，无注入面
        int rows = productMapper.update(null,
                new LambdaUpdateWrapper<Product>()
                        .eq(Product::getPId, pId)
                        .ge(Product::getStock, need)
                        .setSql("stock = stock - " + need));
        if (rows == 0) {
            throw new BusinessException("库存不足（并发）：pId=" + pId);
        }
    }

    /** 根据生产日期+保质期计算 isExpired 字段 */
    private void refreshExpiredFlag(Product product) {
        if (product.getProductionDate() == null || product.getShelfLife() == null) {
            return;
        }
        long shelfMs = product.getShelfLife() * MILLIS_PER_DAY;
        long expireAt = product.getProductionDate().getTime() + shelfMs;
        boolean expired = System.currentTimeMillis() > expireAt;
        product.setIsExpired(expired ? 1 : 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String markExpiredProducts() {
        long start = System.currentTimeMillis();
        // 一、把日期已过期、但标记还停在 is_expired=0 的商品刷成过期。
        //     过期判断直接下推到 SQL，避免全表拉到内存过滤（数据量大时 OOM）
        int marked = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getIsExpired, 0)
                .isNotNull(Product::getProductionDate)
                .isNotNull(Product::getShelfLife)
                .apply("DATE_ADD(production_date, INTERVAL shelf_life DAY) < NOW()")
                .set(Product::getIsExpired, 1));
        // 二、过期商品一律下架。故意用独立一条 SQL 而不是并进上面的 set：这样除了本轮新过期的，
        //     历史遗留的「已过期仍在售」和商家手工重新上架的过期商品也会一并被收下架。
        //     status 为 NULL 的行不参与（顾客侧本就只看 status=1，等价于已下架）。
        int offShelf = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getIsExpired, 1)
                .ne(Product::getStatus, STATUS_OFF_SHELF)
                .set(Product::getStatus, STATUS_OFF_SHELF));
        String summary = String.format("markedExpired=%d, offShelf=%d, costMs=%d",
                marked, offShelf, System.currentTimeMillis() - start);
        log.info("[markExpiredProducts] finish, {}", summary);
        return summary;
    }

    @Override
    public void export(ProductQuery query, AudienceScope scope, HttpServletResponse response) throws Exception {
        LambdaQueryWrapper<Product> queryWrapper = buildListWrapper(query.getpName(), query.getProDesc(),
                query.getProductionDateStart(), query.getProductionDateEnd(),
                query.getOrigin(), query.getIsExpired(), scope);
        queryWrapper.orderByDesc(Product::getPId);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode(EXPORT_SHEET_NAME, "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");

        // 分页查询 + 复用 ExcelWriter 分批写入，避免全量载入内存
        try (ExcelWriter writer = EasyExcel.write(response.getOutputStream(), ProductExportVO.class).build()) {
            WriteSheet sheet = EasyExcel.writerSheet(EXPORT_SHEET_NAME).build();
            writePagedRows(queryWrapper, writer, sheet);
        }
    }

    /**
     * 分页拉取商品并逐批写入 Excel；首页即空时写一次空列表保证导出文件带表头。
     */
    private void writePagedRows(LambdaQueryWrapper<Product> queryWrapper, ExcelWriter writer, WriteSheet sheet) {
        int pageNo = 1;
        int serial = 0;
        while (true) {
            Page<Product> page = new Page<>(pageNo, EXPORT_PAGE_SIZE, false);
            List<Product> records = productMapper.selectPage(page, queryWrapper).getRecords();
            if (records == null || records.isEmpty()) {
                if (pageNo == 1) {
                    // 空数据也要写一次空列表，保证导出文件带表头
                    writer.write(new ArrayList<ProductExportVO>(), sheet);
                }
                break;
            }
            List<ProductExportVO> rows = new ArrayList<>(records.size());
            for (Product product : records) {
                rows.add(ProductExportVO.of(product, ++serial));
            }
            writer.write(rows, sheet);
            if (records.size() < EXPORT_PAGE_SIZE) {
                break;
            }
            pageNo++;
        }
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
                WriteSheet sheet = EasyExcel.writerSheet(EXPORT_SHEET_NAME).build();
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

    @Override
    public ResponseDto<Product> addStock(List<Product> products, AudienceScope scope) {
        if (products == null || products.isEmpty()) {
            return ResponseDto.error("补货商品列表为空");
        }
        if (!scope.isUnrestricted()) {
            List<Integer> ids = products.stream().map(Product::getPId).collect(Collectors.toList());
            for (Product db : productMapper.selectBatchIds(ids)) {
                if (!scope.canManage(db.getMerchantId())) {
                    return ResponseDto.error("无权操作商品：" + db.getPName());
                }
            }
        }
        productMapper.batchUpdateStock(products);
        return ResponseDto.success(null);
    }

    private LambdaQueryWrapper<Product> buildExportWrapper(ProductExportQuery q) {
        return buildListWrapper(q.getpName(), q.getProDesc(), q.getProductionDateStart(),
                q.getProductionDateEnd(), q.getOrigin(), q.getIsExpired(), q.getScope())
                .orderByDesc(Product::getPId);
    }
}
