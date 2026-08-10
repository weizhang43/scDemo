package com.example.scorder.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.curry.model.Address;
import com.curry.model.Order;
import com.curry.model.OrderItem;
import com.curry.model.Product;
import com.example.scorder.client.CouponClient;
import com.example.scorder.config.OrderTimeoutProperties;
import com.example.scorder.config.RabbitMqConfig;
import com.example.scorder.dto.PlaceOrderRequest;
import com.example.scorder.mapper.OrderItemMapper;
import com.example.scorder.mapper.OrderMapper;
import com.example.scorder.service.OrderFeignService;
import com.example.scorder.service.UserFeignService;
import exception.BusinessException;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import response.ResponseDto;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.example.scorder.service.impl.OrderServiceImpl.UN_COMMIT_ORDER_STATUS;

/**
 * 下单核心逻辑辅助类：从 OrderServiceImpl 拆出的实际下单流程
 * （校验商品与价格 → 扣库存 → 地址快照 → 锁券 → 写订单与明细 → 投递超时取消消息）。
 * 校验失败统一抛 BusinessException，由 Seata 全局回滚保证已扣库存/已锁券的最终一致。
 */
@Component
public class OrderPlaceHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderPlaceHelper.class);

    /** 扣减库存失败的兜底提示 */
    private static final String MSG_DEDUCT_STOCK_FAILED = "扣减库存失败";

    /** 下单人缺省值 */
    private static final String DEFAULT_ADD_PERSON = "anonymous";

    /** 订单号当日自增 key 的保留天数：跨天后旧 key 不再使用，到期自动清理 */
    private static final int ORDER_NO_KEY_EXPIRE_DAYS = 2;

    /** 订单号流水号格式：至少 4 位，不足补 0 */
    private static final String ORDER_NO_SEQ_PATTERN = "0000";

    @Autowired
    private OrderFeignService orderFeignService;

    @Autowired
    private UserFeignService userFeignService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private CouponClient couponClient;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderTimeoutProperties orderTimeoutProperties;

    /**
     * 实际下单逻辑：扣库存 → 拉地址快照 → 写订单 → 批量写订单商品中间表，附带商品名称快照。
     * 任一步失败抛异常，由调用方的 Seata 全局事务回滚保证最终一致。
     *
     * @param request 下单请求（调用方已校验非空）
     * @return 创建成功的订单
     */
    public ResponseDto<Order> doPlaceOrder(PlaceOrderRequest request) {
        // 1. 先拉服务端权威商品信息：已下架/已删除的直接拒单，同时拿到折后价与名称快照
        Map<Integer, Product> sellable = loadSellableMap(request.getItems());
        checkSellableAndPrice(request.getItems(), sellable);
        // 2. 校验并扣减库存
        deductStock(request.getItems());
        // 3-4. 拉取收货地址快照并创建订单（金额取服务端有效价，券后实付）
        Order order = buildOrder(request, sellable);
        orderMapper.insert(order);
        // 5. 写订单商品中间表，价格与名称均取服务端快照
        insertOrderItems(request.getItems(), sellable, order.getOId());
        sendTimeoutCancelMessage(request.getOrderStatus(), order.getOId());
        return ResponseDto.success(order);
    }

    /**
     * 下单人缺省值处理：为空时落 anonymous。
     */
    public String normalizeAddPerson(String addPerson) {
        return addPerson == null || addPerson.isEmpty() ? DEFAULT_ADD_PERSON : addPerson;
    }

    /**
     * 生成订单编号：ORD + yyyyMMdd + 流水号（当日 Redis 自增，至少 4 位）。
     * 旧实现基于 count(当日订单)+1，并发下会生成重复单号；Redis INCR 原子自增无此问题。
     */
    public String generateOrderNo() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dayPrefix = sdf.format(new Date());
        RAtomicLong seqCounter = redissonClient.getAtomicLong("order:no:seq:" + dayPrefix);
        long seq = seqCounter.incrementAndGet();
        if (seq == 1) {
            seqCounter.expire(ORDER_NO_KEY_EXPIRE_DAYS, TimeUnit.DAYS);
        }
        DecimalFormat df = new DecimalFormat(ORDER_NO_SEQ_PATTERN);
        return "ORD" + dayPrefix + df.format(seq);
    }

    /**
     * 拉取收货地址快照文本；失败仅记日志返回空串，不阻断下单。
     *
     * @param addressId 收货地址ID（可空）
     * @return 地址单行文本，取不到时为空串
     */
    public String fetchAddressSnapshot(Integer addressId) {
        if (addressId == null) {
            return "";
        }
        try {
            ResponseDto<Address> addrResp = userFeignService.getAddress(addressId);
            if (OrderSupport.isSuccess(addrResp) && addrResp.getDaoResult() != null) {
                Address addr = JSON.parseObject(JSON.toJSONString(addrResp.getDaoResult()), Address.class);
                return OrderSupport.buildAddressText(addr);
            }
        } catch (Exception e) {
            LOGGER.warn("拉取收货地址失败 addressId={}", addressId, e);
        }
        return "";
    }

    /**
     * 拉取服务端在售商品并按商品ID建索引（只含上架商品，查不到即已下架或不存在）。
     */
    private Map<Integer, Product> loadSellableMap(List<PlaceOrderRequest.Item> items) {
        List<Integer> pIds = new ArrayList<>();
        for (PlaceOrderRequest.Item it : items) {
            pIds.add(it.getPId());
        }
        Map<Integer, Product> sellable = new HashMap<>();
        ResponseDto<Product> prodResp = orderFeignService.listSellableByIds(pIds);
        if (OrderSupport.isSuccess(prodResp) && prodResp.getDataList() != null) {
            for (Product p : prodResp.getDataList()) {
                if (p.getPId() != null) {
                    sellable.put(p.getPId(), p);
                }
            }
        }
        return sellable;
    }

    /**
     * 校验每个下单商品均在售且前端展示价与服务端有效价一致，不一致直接拒单让用户看到新价格。
     */
    private void checkSellableAndPrice(List<PlaceOrderRequest.Item> items, Map<Integer, Product> sellable) {
        for (PlaceOrderRequest.Item it : items) {
            Product p = sellable.get(it.getPId());
            if (p == null) {
                throw new BusinessException("商品已下架或不存在，无法下单");
            }
            // 前端展示价与服务端有效价不一致：促销恰在下单瞬间开始/结束，拒单让用户看到新价格
            if (it.getExpectedPrice() != null
                    && it.getExpectedPrice().compareTo(OrderSupport.effectivePriceOf(p)) != 0) {
                throw new BusinessException("商品「" + p.getPName() + "」价格已更新，请刷新后重新下单");
            }
        }
    }

    /**
     * 远程校验并扣减库存，库存不足抛异常终止下单。
     */
    private void deductStock(List<PlaceOrderRequest.Item> items) {
        List<Product> deductItems = new ArrayList<>();
        for (PlaceOrderRequest.Item it : items) {
            Product p = new Product();
            p.setPId(it.getPId());
            p.setStock(it.getQuantity());
            deductItems.add(p);
        }
        ResponseDto<Product> deduct = orderFeignService.checkAndDeductStock(deductItems);
        if (!OrderSupport.isSuccess(deduct)) {
            String msg = deduct == null ? MSG_DEDUCT_STOCK_FAILED : deduct.getMsg();
            throw new BusinessException(msg == null ? MSG_DEDUCT_STOCK_FAILED : msg);
        }
    }

    /**
     * 组装订单：地址快照、订单号、金额（服务端有效价合计，用券时为券后实付）。
     */
    private Order buildOrder(PlaceOrderRequest request, Map<Integer, Product> sellable) {
        Order order = new Order();
        order.setAddPerson(normalizeAddPerson(request.getAddPerson()));
        order.setUId(request.getUId());
        order.setCreateTime(new Date());
        order.setOrderStatus(request.getOrderStatus());
        order.setOrderNo(generateOrderNo());
        order.setOrderAddress(fetchAddressSnapshot(request.getAddressId()));

        // 订单金额：sum(服务端有效价 * quantity)，不采信前端传来的价格
        BigDecimal amount = calcAmount(request.getItems(), sellable);
        if (request.getCouponId() != null) {
            amount = applyCoupon(request, order, amount);
        }
        order.setOrderAmount(amount);
        return order;
    }

    /**
     * 按服务端有效价合计订单金额。
     */
    private BigDecimal calcAmount(List<PlaceOrderRequest.Item> items, Map<Integer, Product> sellable) {
        BigDecimal amount = BigDecimal.ZERO;
        for (PlaceOrderRequest.Item it : items) {
            BigDecimal price = OrderSupport.effectivePriceOf(sellable.get(it.getPId()));
            int qty = it.getQuantity() == null ? 0 : it.getQuantity();
            amount = amount.add(price.multiply(BigDecimal.valueOf(qty)));
        }
        return amount;
    }

    /**
     * 优惠券：锁券(0→1)并按服务端权威规则取抵扣额，返回券后实付金额。
     * 库存已扣，此处任何失败必须抛异常走 Seata 全局回滚（连带回滚已扣库存与券锁定），
     * 直接返回错误会漏放已扣的库存。
     */
    private BigDecimal applyCoupon(PlaceOrderRequest request, Order order, BigDecimal amount) {
        ResponseDto<Object> lockResp = couponClient.lock(request.getCouponId(), request.getUId(), amount);
        if (!OrderSupport.isSuccess(lockResp) || lockResp.getDaoResult() == null) {
            String msg = lockResp == null ? null : lockResp.getMsg();
            throw new BusinessException(msg == null ? "优惠券不可用" : msg);
        }
        JSONObject locked = JSON.parseObject(JSON.toJSONString(lockResp.getDaoResult()));
        BigDecimal deduction = locked.getBigDecimal("couponAmount");
        if (deduction == null || deduction.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("优惠券抵扣金额异常");
        }
        BigDecimal payAmount = amount.subtract(deduction).max(BigDecimal.ZERO);
        // 页面展示的券后价与服务端重算不符：价格或券规则恰在下单瞬间变化，拒单让用户看到新金额
        if (request.getExpectedPayAmount() != null
                && request.getExpectedPayAmount().compareTo(payAmount) != 0) {
            throw new BusinessException("订单金额已变化，请刷新后重新下单");
        }
        order.setCouponId(request.getCouponId());
        order.setCouponAmount(deduction);
        return payAmount;
    }

    /**
     * 批量写入订单商品中间表，价格与名称均取服务端快照。
     */
    private void insertOrderItems(List<PlaceOrderRequest.Item> items,
                                  Map<Integer, Product> sellable, Integer oId) {
        List<OrderItem> itemRecords = new ArrayList<>();
        for (PlaceOrderRequest.Item it : items) {
            Product p = sellable.get(it.getPId());
            OrderItem oi = new OrderItem();
            oi.setOId(oId);
            oi.setPId(it.getPId());
            oi.setQuantity(it.getQuantity());
            oi.setPrice(OrderSupport.effectivePriceOf(p));
            oi.setPName(p.getPName());
            itemRecords.add(oi);
        }
        if (!itemRecords.isEmpty()) {
            orderItemMapper.insertBatch(itemRecords);
        }
    }

    /**
     * 待付款订单投递超时取消消息：取下单时刻的 Nacos 配置值作为消息级 TTL，
     * 到期死信到 dlx_queue 触发取消。
     */
    private void sendTimeoutCancelMessage(Integer orderStatus, Integer oId) {
        if (UN_COMMIT_ORDER_STATUS.compareTo(orderStatus) != 0) {
            return;
        }
        final String ttl = String.valueOf(orderTimeoutProperties.getTimeoutMillis());
        rabbitTemplate.convertAndSend("", RabbitMqConfig.QUEUE_ORDER, oId, message -> {
            message.getMessageProperties().setExpiration(ttl);
            return message;
        });
    }
}
