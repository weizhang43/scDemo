package com.example.scproduct.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.scproduct.auth.AudienceScope;
import com.example.scproduct.entity.CouponTemplate;
import com.example.scproduct.entity.UserCoupon;
import com.example.scproduct.mapper.CouponTemplateMapper;
import com.example.scproduct.mapper.UserCouponMapper;
import com.example.scproduct.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 优惠券。领券防超发完整复刻秒杀（Lua 原子预扣 + DB 兜底 + 失败补偿），
 * 券状态流转全部走条件 UPDATE(CAS)，供 sc-order 经 Feign 在下单/支付/取消/退款链路调用。
 */
@Service
@Slf4j
public class CouponServiceImpl extends ServiceImpl<CouponTemplateMapper, CouponTemplate>
        implements CouponService {

    /** 券余量 key：coupon:stock:{templateId}，String 类型 */
    private static final String COUPON_STOCK_KEY = "coupon:stock:";
    /** 已领用户集合 key：coupon:claimed:{templateId}，Set 类型，一人一张 */
    private static final String COUPON_CLAIMED_KEY = "coupon:claimed:";

    private static final int STATUS_VALID = 1;
    /** 用户券状态 */
    private static final int UC_UNUSED = 0;
    private static final int UC_LOCKED = 1;
    private static final int UC_USED = 2;
    /** 券类型 */
    private static final int TYPE_OFF = 1;
    private static final int TYPE_DISCOUNT = 2;
    /** Redis key 在有效期结束后的保留时长，留足在途补偿时间（同秒杀） */
    private static final long KEY_GRACE_MILLIS = 30 * 60 * 1000L;

    /**
     * 领券 Lua：原子完成 存在性校验 + 一人一张校验 + 余量扣减 + 记录已领（同秒杀模板）。
     * 返回：1 成功；0 领罄；-1 已领过；-2 余量未就绪。
     */
    private static final String CLAIM_LUA =
            "local stock = redis.call('get', KEYS[1]) " +
            "if stock == false then return -2 end " +
            "if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then return -1 end " +
            "if tonumber(stock) <= 0 then return 0 end " +
            "redis.call('decr', KEYS[1]) " +
            "redis.call('sadd', KEYS[2], ARGV[1]) " +
            "redis.call('expire', KEYS[2], ARGV[2]) " +
            "return 1";

    /** 补偿 Lua：归还前判 exists，key 已过期时无条件 INCR 会把停用的券复活成「剩 1 张」 */
    private static final String CLAIM_ROLLBACK_LUA =
            "if ARGV[2] == '1' and redis.call('exists', KEYS[1]) == 1 then " +
            "  redis.call('incr', KEYS[1]) " +
            "end " +
            "redis.call('srem', KEYS[2], ARGV[1]) " +
            "return 1";

    @Autowired
    private CouponTemplateMapper templateMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public ResponseDto<CouponTemplate> createTemplate(CouponTemplate template, AudienceScope scope) {
        if (template == null || template.getName() == null || template.getName().trim().isEmpty()) {
            return ResponseDto.error("券名称不能为空");
        }
        if (scope.isOnSaleOnly()) {
            return ResponseDto.error("无权发布优惠券");
        }
        Integer type = template.getType();
        if (type == null || (type != TYPE_OFF && type != TYPE_DISCOUNT)) {
            return ResponseDto.error("券类型必须是 1(满减) 或 2(折扣)");
        }
        if (type == TYPE_OFF) {
            if (template.getOffAmount() == null || template.getOffAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseDto.error("满减券的减免金额必须大于 0");
            }
            template.setDiscountRate(null);
        } else {
            BigDecimal rate = template.getDiscountRate();
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
                return ResponseDto.error("折扣率必须在 0 到 1 之间（如 0.85 为 85 折）");
            }
            template.setOffAmount(null);
        }
        if (template.getThresholdAmount() == null
                || template.getThresholdAmount().compareTo(BigDecimal.ZERO) < 0) {
            template.setThresholdAmount(BigDecimal.ZERO);
        }
        if (template.getTotalCount() == null || template.getTotalCount() < 1) {
            return ResponseDto.error("发行总量必须大于 0");
        }
        Date start = template.getValidStart();
        Date end = template.getValidEnd();
        if (start == null || end == null) {
            return ResponseDto.error("有效期起止时间不能为空");
        }
        if (!end.after(start)) {
            return ResponseDto.error("有效期结束必须晚于开始");
        }
        if (end.before(new Date())) {
            return ResponseDto.error("有效期结束不能早于当前时间");
        }
        // 商家只能以自己名义发券；平台券(merchant_id=0)仅管理员/内部可发
        if (scope.getMerchantId() != null) {
            template.setMerchantId(scope.getMerchantId());
        } else if (template.getMerchantId() == null) {
            template.setMerchantId(0);
        }
        template.setId(null);
        template.setRemainCount(template.getTotalCount());
        template.setStatus(STATUS_VALID);
        template.setCreateTime(new Date());
        templateMapper.insert(template);
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<CouponTemplate> pageQuery(int pageNo, int pageSize, AudienceScope scope) {
        if (scope.isOnSaleOnly()) {
            return ResponseDto.error("无权查看券模板");
        }
        LambdaQueryWrapper<CouponTemplate> wrapper = new LambdaQueryWrapper<CouponTemplate>()
                .orderByDesc(CouponTemplate::getCreateTime)
                .orderByDesc(CouponTemplate::getId);
        if (scope.getMerchantId() != null) {
            wrapper.in(CouponTemplate::getMerchantId, scope.getMerchantId(), 0);
        }
        IPage<CouponTemplate> result = templateMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        fillRedisRemain(result.getRecords());
        return ResponseDto.success(result);
    }

    @Override
    public ResponseDto<CouponTemplate> disable(Integer id, AudienceScope scope) {
        if (id == null) {
            return ResponseDto.error("模板ID不能为空");
        }
        CouponTemplate template = templateMapper.selectById(id);
        if (template == null) {
            return ResponseDto.error("券模板不存在");
        }
        if (!scope.canManage(template.getMerchantId())) {
            return ResponseDto.error("无权停用该券");
        }
        template.setStatus(0);
        templateMapper.updateById(template);
        zeroStock(id);
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<CouponTemplate> center(Integer uId) {
        List<CouponTemplate> list = templateMapper.selectList(new LambdaQueryWrapper<CouponTemplate>()
                .eq(CouponTemplate::getStatus, STATUS_VALID)
                .ge(CouponTemplate::getValidEnd, new Date())
                .orderByDesc(CouponTemplate::getCreateTime));
        fillRedisRemain(list);
        if (uId != null && !list.isEmpty()) {
            List<Integer> templateIds = new ArrayList<>();
            for (CouponTemplate t : list) {
                templateIds.add(t.getId());
            }
            List<UserCoupon> mine = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getUId, uId)
                    .in(UserCoupon::getTemplateId, templateIds));
            Set<Integer> claimedIds = new HashSet<>();
            for (UserCoupon uc : mine) {
                claimedIds.add(uc.getTemplateId());
            }
            for (CouponTemplate t : list) {
                t.setClaimed(claimedIds.contains(t.getId()));
            }
        }
        return ResponseDto.success(list);
    }

    @Override
    public ResponseDto<UserCoupon> claim(Integer templateId, Integer uId) {
        if (templateId == null || uId == null) {
            return ResponseDto.error("参数不能为空");
        }
        CouponTemplate template = templateMapper.selectById(templateId);
        if (template == null || template.getStatus() == null || template.getStatus() != STATUS_VALID) {
            return ResponseDto.error("优惠券不存在或已停用");
        }
        Date now = new Date();
        if (template.getValidEnd() != null && template.getValidEnd().before(now)) {
            return ResponseDto.error("优惠券已过期");
        }
        long ttlMillis = template.getValidEnd().getTime() + KEY_GRACE_MILLIS - now.getTime();
        String stockKey = COUPON_STOCK_KEY + templateId;
        String claimedKey = COUPON_CLAIMED_KEY + templateId;

        // 懒播种：值取 DB remain_count，SETNX 保证并发下只有首个线程播种成功
        RBucket<String> stockBucket = redissonClient.getBucket(stockKey, StringCodec.INSTANCE);
        if (!stockBucket.isExists()) {
            int remain = template.getRemainCount() == null ? 0 : template.getRemainCount();
            if (remain < 1) {
                return ResponseDto.error("该券已领完");
            }
            stockBucket.trySet(String.valueOf(remain), ttlMillis, TimeUnit.MILLISECONDS);
        }

        List<Object> keys = new ArrayList<>();
        keys.add(stockKey);
        keys.add(claimedKey);
        Long code = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE, CLAIM_LUA, RScript.ReturnType.INTEGER,
                keys, String.valueOf(uId), String.valueOf(Math.max(ttlMillis / 1000L, 1L)));
        long r = code == null ? 0L : code;
        if (r == -1L) {
            return ResponseDto.error("您已领取过该券");
        }
        if (r == -2L) {
            return ResponseDto.error("领券未就绪，请稍后重试");
        }
        if (r != 1L) {
            return ResponseDto.error("该券已领完");
        }

        // DB 落库：remain_count 条件扣减兜底，失败一律回滚 Lua 预扣
        try {
            int deducted = templateMapper.deductRemain(templateId);
            if (deducted == 0) {
                rollbackClaim(templateId, uId, true);
                return ResponseDto.error("该券已领完");
            }
            UserCoupon coupon = new UserCoupon();
            coupon.setTemplateId(templateId);
            coupon.setUId(uId);
            coupon.setStatus(UC_UNUSED);
            coupon.setClaimTime(now);
            userCouponMapper.insert(coupon);
            return ResponseDto.success(coupon);
        } catch (DuplicateKeyException e) {
            // Redis 已领集合丢失时唯一索引兜底；把 DB 扣掉的余量还回去
            templateMapper.restoreRemain(templateId);
            rollbackClaim(templateId, uId, true);
            return ResponseDto.error("您已领取过该券");
        } catch (Exception e) {
            log.error("[coupon] 领券落库失败 templateId={}, uId={}", templateId, uId, e);
            rollbackClaim(templateId, uId, true);
            return ResponseDto.error("领券失败，请稍后重试");
        }
    }

    private void rollbackClaim(Integer templateId, Integer uId, boolean restoreStock) {
        List<Object> keys = new ArrayList<>();
        keys.add(COUPON_STOCK_KEY + templateId);
        keys.add(COUPON_CLAIMED_KEY + templateId);
        redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE, CLAIM_ROLLBACK_LUA, RScript.ReturnType.INTEGER,
                keys, String.valueOf(uId), restoreStock ? "1" : "0");
    }

    /** 余量置 0 而非删 key：删了之后在途补偿的 INCR 会把它重建成 1（同秒杀） */
    private void zeroStock(Integer templateId) {
        RBucket<String> bucket = redissonClient.getBucket(COUPON_STOCK_KEY + templateId, StringCodec.INSTANCE);
        if (bucket.isExists()) {
            bucket.set("0", bucket.remainTimeToLive(), TimeUnit.MILLISECONDS);
        }
    }

    private void fillRedisRemain(List<CouponTemplate> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (CouponTemplate t : list) {
            RBucket<String> bucket = redissonClient.getBucket(
                    COUPON_STOCK_KEY + t.getId(), StringCodec.INSTANCE);
            String value = bucket.get();
            if (value != null) {
                try {
                    t.setRedisRemain(Integer.valueOf(value));
                    continue;
                } catch (NumberFormatException ignored) {
                    log.warn("[coupon] 余量值非法 templateId={}, value={}", t.getId(), value);
                }
            }
            t.setRedisRemain(t.getRemainCount() == null ? 0 : t.getRemainCount());
        }
    }

    @Override
    public ResponseDto<UserCoupon> mine(Integer uId, Integer status) {
        if (uId == null) {
            return ResponseDto.error("用户ID不能为空");
        }
        return ResponseDto.success(userCouponMapper.selectMine(uId, status));
    }

    @Override
    public ResponseDto<UserCoupon> usable(Integer uId, BigDecimal orderAmount) {
        if (uId == null || orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseDto.error("参数不能为空");
        }
        List<UserCoupon> unused = userCouponMapper.selectMine(uId, UC_UNUSED);
        List<UserCoupon> result = new ArrayList<>();
        for (UserCoupon c : unused) {
            BigDecimal deduction = calcDeduction(c, orderAmount);
            if (deduction != null && deduction.compareTo(BigDecimal.ZERO) > 0) {
                c.setCouponAmount(deduction);
                result.add(c);
            }
        }
        return ResponseDto.success(result);
    }

    @Override
    public ResponseDto<UserCoupon> lock(Integer couponId, Integer uId, BigDecimal orderAmount) {
        if (couponId == null || uId == null || orderAmount == null
                || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseDto.error("参数不能为空");
        }
        UserCoupon coupon = userCouponMapper.selectDetailById(couponId);
        if (coupon == null) {
            return ResponseDto.error("优惠券不存在");
        }
        if (!uId.equals(coupon.getUId())) {
            return ResponseDto.error("该券不属于当前用户");
        }
        if (coupon.getStatus() == null || coupon.getStatus() != UC_UNUSED) {
            return ResponseDto.error("该券已被占用或已使用");
        }
        BigDecimal deduction = calcDeduction(coupon, orderAmount);
        if (deduction == null) {
            return ResponseDto.error("该券不满足使用条件（有效期或使用门槛）");
        }
        int rows = userCouponMapper.casLock(couponId, uId, deduction);
        if (rows == 0) {
            return ResponseDto.error("该券已被占用，请刷新后重试");
        }
        coupon.setStatus(UC_LOCKED);
        coupon.setCouponAmount(deduction);
        return ResponseDto.success(coupon);
    }

    /**
     * 抵扣额：满减取 min(减免额, 订单额)；折扣按 (1-折扣率) 对订单额计算，保留 2 位。
     * 不满足有效期或门槛返回 null。
     */
    private BigDecimal calcDeduction(UserCoupon coupon, BigDecimal orderAmount) {
        Date now = new Date();
        if (coupon.getValidStart() != null && now.before(coupon.getValidStart())) {
            return null;
        }
        if (coupon.getValidEnd() != null && now.after(coupon.getValidEnd())) {
            return null;
        }
        if (coupon.getThresholdAmount() != null
                && orderAmount.compareTo(coupon.getThresholdAmount()) < 0) {
            return null;
        }
        Integer type = coupon.getType();
        if (type != null && type == TYPE_DISCOUNT) {
            if (coupon.getDiscountRate() == null) {
                return null;
            }
            return orderAmount.multiply(BigDecimal.ONE.subtract(coupon.getDiscountRate()))
                    .setScale(2, RoundingMode.HALF_UP)
                    .min(orderAmount);
        }
        if (coupon.getOffAmount() == null) {
            return null;
        }
        return coupon.getOffAmount().min(orderAmount);
    }

    @Override
    public ResponseDto<UserCoupon> use(Integer couponId, Integer oId) {
        if (couponId == null || oId == null) {
            return ResponseDto.error("参数不能为空");
        }
        int rows = userCouponMapper.casUse(couponId, oId);
        if (rows > 0) {
            return ResponseDto.success(null);
        }
        UserCoupon current = userCouponMapper.selectById(couponId);
        if (current != null && current.getStatus() != null && current.getStatus() == UC_USED
                && Objects.equals(current.getOId(), oId)) {
            // 支付回调重放：已核销到同一订单，幂等成功
            return ResponseDto.success(null);
        }
        return ResponseDto.error("券核销失败：状态不是已锁定");
    }

    @Override
    public ResponseDto<UserCoupon> restore(Integer couponId) {
        if (couponId == null) {
            return ResponseDto.error("参数不能为空");
        }
        userCouponMapper.casRestore(couponId);
        return ResponseDto.success(null);
    }
}
