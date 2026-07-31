package com.example.scproduct.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Product;
import com.curry.model.SeckillActivity;
import com.example.scproduct.auth.AudienceScope;
import com.example.scproduct.mapper.ProductMapper;
import com.example.scproduct.mapper.SeckillActivityMapper;
import com.example.scproduct.service.SeckillActivityService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀活动。名额与已购标记全部按 activityId 计，同一商品的多场活动互不干扰。
 * 直接依赖 ProductMapper 而非 ProductService：只需要读商品行，走 Service 层会引入不必要的耦合。
 */
@Service
@Slf4j
public class SeckillActivityServiceImpl extends ServiceImpl<SeckillActivityMapper, SeckillActivity>
        implements SeckillActivityService {

    /** 秒杀名额 key：seckill:stock:{activityId}，String 类型 */
    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    /** 秒杀已购用户集合 key：seckill:bought:{activityId}，Set 类型，用于一人一单 */
    private static final String SECKILL_BOUGHT_KEY = "seckill:bought:";

    /** 活动状态：1-有效 0-已取消 */
    private static final int STATUS_VALID = 1;
    /** 商品上架状态 */
    private static final int PRODUCT_ON_SALE = 1;
    /** Redis key 在活动结束后的保留时长：留足在途补偿的时间，之后自然过期，不残留僵尸 key */
    private static final long KEY_GRACE_MILLIS = 30 * 60 * 1000L;

    /**
     * 秒杀预扣 Lua：原子完成 一人一单校验 + 余量判断 + 扣减 + 记录已购。
     * ARGV[1]=uId，ARGV[2]=已购集合的 TTL 秒数（每次续到活动结束，避免集合永久驻留）。
     * 返回：1 成功；0 售罄；-1 已参与；-2 名额未就绪。
     */
    private static final String SECKILL_LUA =
            "local stock = redis.call('get', KEYS[1]) " +
            "if stock == false then return -2 end " +
            "if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then return -1 end " +
            "if tonumber(stock) <= 0 then return 0 end " +
            "redis.call('decr', KEYS[1]) " +
            "redis.call('sadd', KEYS[2], ARGV[1]) " +
            "redis.call('expire', KEYS[2], ARGV[2]) " +
            "return 1";

    /**
     * 秒杀补偿 Lua：移除已购标记，ARGV[2]='1' 时才归还名额。
     * 归还前先判 exists —— key 已过期时无条件 INCR 会把活动凭空复活成「剩 1 个名额」。
     */
    private static final String SECKILL_ROLLBACK_LUA =
            "if ARGV[2] == '1' and redis.call('exists', KEYS[1]) == 1 then " +
            "  redis.call('incr', KEYS[1]) " +
            "end " +
            "redis.call('srem', KEYS[2], ARGV[1]) " +
            "return 1";

    @Autowired
    private SeckillActivityMapper activityMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public ResponseDto<SeckillActivity> create(SeckillActivity activity, AudienceScope scope) {
        if (activity == null || activity.getPId() == null) {
            return ResponseDto.error("商品ID不能为空");
        }
        BigDecimal seckillPrice = activity.getSeckillPrice();
        if (seckillPrice == null || seckillPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseDto.error("秒杀价必须大于 0");
        }
        Integer seckillStock = activity.getSeckillStock();
        if (seckillStock == null || seckillStock < 1) {
            return ResponseDto.error("秒杀名额必须大于 0");
        }
        Date start = activity.getStartTime();
        Date end = activity.getEndTime();
        if (start == null || end == null) {
            return ResponseDto.error("活动起止时间不能为空");
        }
        if (!end.after(start)) {
            return ResponseDto.error("结束时间必须晚于开始时间");
        }
        if (end.before(new Date())) {
            return ResponseDto.error("结束时间不能早于当前时间");
        }
        Product product = productMapper.selectById(activity.getPId());
        if (product == null) {
            return ResponseDto.error("商品不存在");
        }
        if (!scope.canManage(product.getMerchantId())) {
            return ResponseDto.error("无权为该商品发布秒杀活动");
        }
        if (product.getStatus() != null && product.getStatus() != PRODUCT_ON_SALE) {
            return ResponseDto.error("商品已下架，请先上架再发布秒杀");
        }
        if (product.getPrice() != null && seckillPrice.compareTo(BigDecimal.valueOf(product.getPrice())) >= 0) {
            return ResponseDto.error("秒杀价必须低于原价 " + product.getPrice() + " 元");
        }
        // 时间窗重叠即拒绝：同一商品同时多场秒杀，顾客端无法确定该按哪个价格抢
        long overlap = activityMapper.selectCount(new LambdaQueryWrapper<SeckillActivity>()
                .eq(SeckillActivity::getPId, activity.getPId())
                .eq(SeckillActivity::getStatus, STATUS_VALID)
                .lt(SeckillActivity::getStartTime, end)
                .gt(SeckillActivity::getEndTime, start));
        if (overlap > 0) {
            return ResponseDto.error("该商品在这个时间段已有秒杀活动，请调整时间");
        }
        // 名额只是上限、不预扣真实库存，但划出的总量必须有真实库存兜着，
        // 否则会出现「预扣成功 → 扣真实库存失败」的空转
        int productStock = product.getStock() == null ? 0 : product.getStock();
        int reserved = activityMapper.sumReservedStock(activity.getPId(), null);
        if (reserved + seckillStock > productStock) {
            return ResponseDto.error("秒杀名额超出可用库存：商品库存 " + productStock
                    + "，其他进行中活动已划出 " + reserved);
        }
        activity.setId(null);
        activity.setStatus(STATUS_VALID);
        activity.setCreateTime(new Date());
        activityMapper.insert(activity);
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<SeckillActivity> cancel(Integer id, AudienceScope scope) {
        if (id == null) {
            return ResponseDto.error("活动ID不能为空");
        }
        SeckillActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            return ResponseDto.error("秒杀活动不存在");
        }
        Product product = productMapper.selectById(activity.getPId());
        if (product != null && !scope.canManage(product.getMerchantId())) {
            return ResponseDto.error("无权取消该秒杀活动");
        }
        activityMapper.update(null, new LambdaUpdateWrapper<SeckillActivity>()
                .eq(SeckillActivity::getId, id)
                .set(SeckillActivity::getStatus, 0));
        zeroStock(id);
        return ResponseDto.success(null);
    }

    @Override
    public int endByProduct(Integer pId) {
        if (pId == null) {
            return 0;
        }
        List<SeckillActivity> unfinished = activityMapper.selectList(new LambdaQueryWrapper<SeckillActivity>()
                .eq(SeckillActivity::getPId, pId)
                .eq(SeckillActivity::getStatus, STATUS_VALID)
                .ge(SeckillActivity::getEndTime, new Date()));
        if (unfinished.isEmpty()) {
            return 0;
        }
        List<Integer> ids = new ArrayList<>();
        for (SeckillActivity a : unfinished) {
            ids.add(a.getId());
        }
        activityMapper.update(null, new LambdaUpdateWrapper<SeckillActivity>()
                .in(SeckillActivity::getId, ids)
                .set(SeckillActivity::getStatus, 0));
        for (Integer id : ids) {
            zeroStock(id);
        }
        return ids.size();
    }

    /** 名额置 0 而非删 key：删了之后在途补偿的 INCR 会把它重建成 1 */
    private void zeroStock(Integer activityId) {
        RBucket<String> bucket = redissonClient.getBucket(SECKILL_STOCK_KEY + activityId, StringCodec.INSTANCE);
        if (bucket.isExists()) {
            bucket.set("0", bucket.remainTimeToLive(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public ResponseDto<SeckillActivity> pageQuery(Integer pId, int pageNo, int pageSize, AudienceScope scope) {
        Page<SeckillActivity> page = new Page<>(pageNo, pageSize);
        IPage<SeckillActivity> result = activityMapper.selectActivityPage(page, pId, scope.getMerchantId());
        fillRemainStock(result.getRecords());
        return ResponseDto.success(result);
    }

    @Override
    public ResponseDto<SeckillActivity> listForCustomer() {
        List<SeckillActivity> list = activityMapper.selectUpcomingAndRunning();
        fillRemainStock(list);
        return ResponseDto.success(list);
    }

    @Override
    public SeckillActivity detail(Integer id) {
        if (id == null) {
            return null;
        }
        SeckillActivity activity = activityMapper.selectDetailById(id);
        if (activity != null) {
            fillRemainStock(Collections.singletonList(activity));
        }
        return activity;
    }

    @Override
    public ResponseDto<SeckillActivity> preDeduct(Integer activityId, Integer uId) {
        if (activityId == null || uId == null) {
            return ResponseDto.error("参数不能为空");
        }
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null || activity.getStatus() == null || activity.getStatus() != STATUS_VALID) {
            return ResponseDto.error("秒杀活动不存在或已取消");
        }
        Date now = new Date();
        if (activity.getStartTime() != null && activity.getStartTime().after(now)) {
            return ResponseDto.error("秒杀还未开始");
        }
        if (activity.getEndTime() != null && activity.getEndTime().before(now)) {
            return ResponseDto.error("秒杀已结束");
        }
        long ttlMillis = activity.getEndTime().getTime() + KEY_GRACE_MILLIS - now.getTime();
        String stockKey = SECKILL_STOCK_KEY + activityId;
        String boughtKey = SECKILL_BOUGHT_KEY + activityId;

        // 懒加载播种：名额取 min(活动名额, 商品当前库存)，避免划出比真实库存还多的幽灵名额
        RBucket<String> stockBucket = redissonClient.getBucket(stockKey, StringCodec.INSTANCE);
        if (!stockBucket.isExists()) {
            Product db = productMapper.selectById(activity.getPId());
            if (db == null) {
                return ResponseDto.error("商品不可秒杀：商品不存在");
            }
            if (db.getIsExpired() != null && db.getIsExpired() == 1) {
                return ResponseDto.error("商品不可秒杀：已过期");
            }
            if (db.getStatus() != null && db.getStatus() != PRODUCT_ON_SALE) {
                return ResponseDto.error("商品不可秒杀：已下架");
            }
            int productStock = db.getStock() == null ? 0 : db.getStock();
            int seed = Math.min(activity.getSeckillStock() == null ? 0 : activity.getSeckillStock(), productStock);
            if (seed < 1) {
                return ResponseDto.error("已售罄");
            }
            // trySet 即 SETNX，并发下只有首个线程播种成功，其余直接进入 Lua
            stockBucket.trySet(String.valueOf(seed), ttlMillis, TimeUnit.MILLISECONDS);
        }

        List<Object> keys = new ArrayList<>();
        keys.add(stockKey);
        keys.add(boughtKey);
        Long code = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE, SECKILL_LUA, RScript.ReturnType.INTEGER,
                keys, String.valueOf(uId), String.valueOf(Math.max(ttlMillis / 1000L, 1L)));
        long r = code == null ? 0L : code;
        if (r == 1L) {
            return ResponseDto.success(null);
        }
        if (r == -1L) {
            return ResponseDto.error("您已参与过该场秒杀");
        }
        if (r == -2L) {
            return ResponseDto.error("秒杀未就绪，请稍后重试");
        }
        return ResponseDto.error("已抢完");
    }

    @Override
    public ResponseDto<SeckillActivity> rollback(Integer activityId, Integer uId, boolean restoreStock) {
        if (activityId == null || uId == null) {
            return ResponseDto.error("参数不能为空");
        }
        List<Object> keys = new ArrayList<>();
        keys.add(SECKILL_STOCK_KEY + activityId);
        keys.add(SECKILL_BOUGHT_KEY + activityId);
        redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE, SECKILL_ROLLBACK_LUA, RScript.ReturnType.INTEGER,
                keys, String.valueOf(uId), restoreStock ? "1" : "0");
        return ResponseDto.success(null);
    }

    /**
     * 回填剩余名额：Redis 未播种时返回活动名额与商品库存的较小值，与首次预扣的播种值保持一致。
     */
    private void fillRemainStock(List<SeckillActivity> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SeckillActivity activity : list) {
            RBucket<String> bucket = redissonClient.getBucket(
                    SECKILL_STOCK_KEY + activity.getId(), StringCodec.INSTANCE);
            String value = bucket.get();
            if (value != null) {
                try {
                    activity.setRemainStock(Integer.valueOf(value));
                    continue;
                } catch (NumberFormatException ignored) {
                    log.warn("[seckill] 名额值非法 activityId={}, value={}", activity.getId(), value);
                }
            }
            int quota = activity.getSeckillStock() == null ? 0 : activity.getSeckillStock();
            Integer productStock = activity.getProductStock();
            activity.setRemainStock(productStock == null ? quota : Math.min(quota, productStock));
        }
    }
}
