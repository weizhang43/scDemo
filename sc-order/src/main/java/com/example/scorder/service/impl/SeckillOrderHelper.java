package com.example.scorder.service.impl;

import com.alibaba.fastjson.JSON;
import com.curry.model.Order;
import com.curry.model.OrderItem;
import com.curry.model.Product;
import com.curry.model.SeckillActivity;
import com.example.scorder.dto.SeckillRequest;
import com.example.scorder.mapper.OrderItemMapper;
import com.example.scorder.mapper.OrderMapper;
import com.example.scorder.service.OrderFeignService;
import com.example.scorder.vo.SeckillResultVO;
import exception.BusinessException;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import response.ResponseDto;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.example.scorder.service.impl.OrderServiceImpl.PLACED_ORDER_STATUS;

/**
 * 秒杀落库辅助类：从 OrderServiceImpl 拆出的异步落库流程与秒杀结果 Redis 读写。
 * 落库失败时补偿回滚 Redis 预扣并写失败结果，再抛异常触发调用方 Seata 全局回滚订单与真实库存。
 */
@Component
public class SeckillOrderHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillOrderHelper.class);

    /** 秒杀结果 key 前缀：seckill:result:{uId}:{activityId} */
    private static final String SECKILL_RESULT_KEY = "seckill:result:";

    /** 秒杀结果缓存时长（小时），供前端轮询 */
    private static final long RESULT_TTL_HOURS = 1L;

    @Autowired
    private OrderFeignService orderFeignService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private OrderPlaceHelper orderPlaceHelper;

    /**
     * 真实库存不足导致的落库失败：名额本就是从库存里划出的上限，
     * 库存已被正常销售抽干说明这批名额是幽灵名额，补偿时不归还名额。
     */
    private static final class StockShortageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        StockShortageException(String message) {
            super(message);
        }
    }

    /**
     * 秒杀落库（异步段，由消费者经 OrderServiceImpl 调用）：
     * 活动快照 → 扣真实库存 → 创建订单与明细 → 写 SUCCESS 结果。
     * 失败则补偿回滚 Redis 预扣并写 FAILED 结果，再抛异常触发 Seata 全局回滚。
     *
     * @param req 秒杀请求
     */
    public void process(SeckillRequest req) {
        try {
            placeSeckillOrder(req);
        } catch (StockShortageException e) {
            compensateAndRethrow(req, true, e);
        } catch (Exception e) {
            compensateAndRethrow(req, false, e);
        }
    }

    /**
     * 将秒杀结果写入 Redis，有效期 1 小时，供前端轮询查询。
     *
     * @param uId        用户ID
     * @param activityId 活动ID
     * @param vo         结果对象（PENDING/SUCCESS/FAILED）
     */
    public void saveResult(Integer uId, Integer activityId, SeckillResultVO vo) {
        RBucket<SeckillResultVO> bucket = redissonClient.getBucket(resultKey(uId, activityId));
        bucket.set(vo, RESULT_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 读取秒杀结果，无记录（已过期或从未发起）返回 null。
     *
     * @param uId        用户ID
     * @param activityId 活动ID
     * @return 结果对象，无记录为 null
     */
    public SeckillResultVO readResult(Integer uId, Integer activityId) {
        RBucket<SeckillResultVO> bucket = redissonClient.getBucket(resultKey(uId, activityId));
        return bucket.get();
    }

    private String resultKey(Integer uId, Integer activityId) {
        return SECKILL_RESULT_KEY + uId + ":" + activityId;
    }

    /**
     * 落库主流程：活动快照（秒杀价与商品名都取服务端，绝不采信请求里的价格）→
     * 扣真实库存（数量1）→ 创建订单与明细 → 写 SUCCESS 结果（Redis 写入不受 Seata 回滚影响）。
     */
    private void placeSeckillOrder(SeckillRequest req) {
        SeckillActivity activity = loadActivity(req.getActivityId());
        deductRealStock(activity.getPId());
        Order order = createSeckillOrder(req, activity);
        saveResult(req.getuId(), req.getActivityId(),
                new SeckillResultVO("SUCCESS", "下单成功", order.getOrderNo()));
    }

    /**
     * 拉取秒杀活动快照，活动缺失或秒杀价缺失直接失败。
     */
    private SeckillActivity loadActivity(Integer activityId) {
        ResponseDto<SeckillActivity> actResp = orderFeignService.getSeckillActivity(activityId);
        if (!OrderSupport.isSuccess(actResp) || actResp.getDaoResult() == null) {
            throw new BusinessException("秒杀活动不存在");
        }
        SeckillActivity activity = JSON.parseObject(
                JSON.toJSONString(actResp.getDaoResult()), SeckillActivity.class);
        if (activity.getSeckillPrice() == null) {
            throw new BusinessException("秒杀价缺失");
        }
        return activity;
    }

    /**
     * 扣真实库存（数量1），条件更新兜底防超卖；失败抛 StockShortageException 标记不归还名额。
     */
    private void deductRealStock(Integer pId) {
        Product item = new Product();
        item.setPId(pId);
        item.setStock(1);
        ResponseDto<Product> deduct = orderFeignService.checkAndDeductStock(Collections.singletonList(item));
        if (!OrderSupport.isSuccess(deduct)) {
            throw new StockShortageException(deduct == null ? "扣减库存失败" : deduct.getMsg());
        }
    }

    /**
     * 创建秒杀订单（金额为秒杀价）与订单商品明细（数量1）。
     */
    private Order createSeckillOrder(SeckillRequest req, SeckillActivity activity) {
        Order order = new Order();
        order.setAddPerson(orderPlaceHelper.normalizeAddPerson(req.getAddPerson()));
        order.setUId(req.getuId());
        order.setCreateTime(new Date());
        order.setOrderStatus(PLACED_ORDER_STATUS);
        order.setOrderNo(orderPlaceHelper.generateOrderNo());
        order.setOrderAddress(orderPlaceHelper.fetchAddressSnapshot(req.getAddressId()));
        order.setOrderAmount(activity.getSeckillPrice());
        orderMapper.insert(order);

        OrderItem oi = new OrderItem();
        oi.setOId(order.getOId());
        oi.setPId(activity.getPId());
        oi.setPName(activity.getPName());
        oi.setQuantity(1);
        oi.setPrice(activity.getSeckillPrice());
        orderItemMapper.insert(oi);
        return order;
    }

    /**
     * 补偿：移除已购标记允许重试；名额是否归还取决于失败原因（库存不足不归还）。
     * 补偿完成后抛业务异常触发 Seata 全局回滚（订单/真实库存）。
     */
    private void compensateAndRethrow(SeckillRequest req, boolean stockShortage, Exception e) {
        Integer uId = req.getuId();
        Integer activityId = req.getActivityId();
        LOGGER.error("[seckill] 落库失败，执行补偿 uId={}, activityId={}", uId, activityId, e);
        try {
            orderFeignService.rollbackSeckillStock(activityId, uId, !stockShortage);
        } catch (Exception ex) {
            LOGGER.error("[seckill] 补偿回滚 Redis 失败 uId={}, activityId={}", uId, activityId, ex);
        }
        saveResult(uId, activityId, new SeckillResultVO("FAILED",
                stockShortage ? "商品库存不足，秒杀失败" : "下单失败，请重试", null));
        throw new BusinessException("秒杀落库失败: " + e.getMessage(), e);
    }
}
