package com.example.scorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Order;
import com.curry.model.OrderItem;
import com.curry.model.OrderMessage;
import com.curry.model.SeckillActivity;
import com.example.scorder.auth.OrderScope;
import com.example.scorder.client.MockPayGatewayClient;
import com.example.scorder.config.OrderTimeoutProperties;
import com.example.scorder.config.RabbitMqConfig;
import com.example.scorder.dto.OrderQueryRequest;
import com.example.scorder.dto.PlaceOrderRequest;
import com.example.scorder.dto.SeckillRequest;
import com.example.scorder.entity.AfterSale;
import com.example.scorder.entity.OrderStockRestoreMsg;
import com.example.scorder.entity.PayRecord;
import com.example.scorder.mapper.AfterSaleMapper;
import com.example.scorder.mapper.OrderItemMapper;
import com.example.scorder.mapper.OrderMapper;
import com.example.scorder.mapper.OrderStockRestoreMsgMapper;
import com.example.scorder.mq.SeckillOrderProducer;
import com.example.scorder.service.OrderFeignService;
import com.example.scorder.service.OrderService;
import com.example.scorder.service.PayService;
import com.example.scorder.vo.DailySalesVO;
import com.example.scorder.vo.DashboardOverviewVO;
import com.example.scorder.vo.MonthlySalesVO;
import com.example.scorder.vo.OrderTimeoutVO;
import com.example.scorder.vo.ProductSalesRankVO;
import com.example.scorder.vo.SeckillResultVO;
import com.example.scorder.vo.TypeSalesVO;
import exception.BusinessException;
import io.seata.spring.annotation.GlobalTransactional;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单核心服务：列表查询/统计、状态机流转（支付/发货/取消/完成）、批量下单与秒杀入口。
 * 下单落库、秒杀落库与 Excel 导出分别拆分到 OrderPlaceHelper、SeckillOrderHelper、OrderExportService。
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);

    /**
     * 订单取消状态码
     */
    public static final Integer CANCEL_ORDER_STATUS = -1;
    /** 已支付待发货状态码 */
    public static final Integer PLACED_ORDER_STATUS = 1;
    /** 已完成状态码 */
    public static final Integer COMPLETE_ORDER_STATUS = 2;
    /** 待付款 */
    public static final Integer UN_COMMIT_ORDER_STATUS = 0;
    /** 已发货待收货状态码 */
    public static final Integer SHIPPED_ORDER_STATUS = 3;

    /** 订单不存在（越权访问也返回同一句，避免探测他人订单） */
    private static final String MSG_ORDER_NOT_FOUND = "订单不存在";
    /** 订单ID缺失 */
    private static final String MSG_ORDER_ID_REQUIRED = "订单ID不能为空";
    /** CAS 更新失败（状态已被并发变更） */
    private static final String MSG_STATUS_CHANGED = "订单状态已变更，请刷新后重试";

    /** 下单防重锁：等待时长（秒） */
    private static final long PLACE_LOCK_WAIT_SECONDS = 3L;
    /** 下单防重锁：持有时长（秒） */
    private static final long PLACE_LOCK_LEASE_SECONDS = 30L;
    /** 回库存本地消息最大重试次数 */
    private static final int STOCK_RESTORE_MAX_RETRY = 5;

    @Autowired
    private OrderFeignService orderFeignService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private SeckillOrderProducer seckillOrderProducer;

    @Autowired
    private OrderStockRestoreMsgMapper orderStockRestoreMsgMapper;

    @Autowired
    private AfterSaleMapper afterSaleMapper;

    @Autowired
    private PayService payService;

    @Autowired
    private MockPayGatewayClient mockPayGatewayClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /** 订单超时时长（分钟）：状态为0的订单以 createTime + 该时长作为倒计时到期点 */
    @Autowired
    private OrderTimeoutProperties orderTimeoutProperties;

    @Autowired
    private OrderPlaceHelper orderPlaceHelper;

    @Autowired
    private SeckillOrderHelper seckillOrderHelper;

    @Autowired
    private OrderExportService orderExportService;

    @Autowired
    private OrderStatsHelper orderStatsHelper;

    @Override
    public ResponseDto<OrderTimeoutVO> listTimeoutWarning() {
        return ResponseDto.success(buildTimeoutWarning(null));
    }

    @Override
    public ResponseDto<OrderTimeoutVO> listMyTimeoutWarning(String uName) {
        // uName 为空时 MP 会跳过该条件，本接口会退化成返回全量订单，必须显式拒绝
        if (!StringUtils.hasText(uName)) {
            return ResponseDto.error("未登录");
        }
        return ResponseDto.success(buildTimeoutWarning(uName));
    }

    /**
     * 查询待付款订单并补出到期时间。addPerson 非空时只查该下单人（t_order.add_person 存的是 uName）。
     */
    private List<OrderTimeoutVO> buildTimeoutWarning(String addPerson) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderStatus, UN_COMMIT_ORDER_STATUS)
                .eq(StringUtils.hasText(addPerson), Order::getAddPerson, addPerson)
                .orderByAsc(Order::getCreateTime);
        List<Order> orders = orderMapper.selectList(wrapper);
        return orders.stream().map(this::toTimeoutVO).collect(Collectors.toList());
    }

    /**
     * 组装超时预警行：到期时间 = createTime + 配置的超时时长。
     */
    private OrderTimeoutVO toTimeoutVO(Order o) {
        OrderTimeoutVO vo = new OrderTimeoutVO();
        vo.setOId(o.getOId());
        vo.setOrderNo(o.getOrderNo());
        vo.setOrderAmount(o.getOrderAmount());
        vo.setOrderStatus(o.getOrderStatus());
        vo.setCreateTime(o.getCreateTime());
        if (o.getCreateTime() != null) {
            vo.setExpireTime(new Date(o.getCreateTime().getTime() + orderTimeoutProperties.getTimeoutMillis()));
        }
        return vo;
    }

    /**
     * 商品销量榜，统计逻辑拆分至 OrderStatsHelper。
     */
    @Override
    public ResponseDto<ProductSalesRankVO> listSalesRank(int limit, Integer merchantId) {
        return orderStatsHelper.listSalesRank(limit, merchantId);
    }

    /**
     * 销量按商品类型分组统计，统计逻辑拆分至 OrderStatsHelper。
     */
    @Override
    public ResponseDto<TypeSalesVO> listTypeSales(Integer merchantId) {
        return orderStatsHelper.listTypeSales(merchantId);
    }

    /**
     * 近三个自然月每月销量（缺月补 0），统计逻辑拆分至 OrderStatsHelper。
     */
    @Override
    public ResponseDto<MonthlySalesVO> listMonthlySales(Integer merchantId) {
        return orderStatsHelper.listMonthlySales(merchantId);
    }

    /**
     * 首页工作台概览指标，统计逻辑拆分至 OrderStatsHelper。
     */
    @Override
    public ResponseDto<DashboardOverviewVO> dashboardOverview(Integer merchantId) {
        return orderStatsHelper.dashboardOverview(merchantId);
    }

    /**
     * 近 N 天逐日成交趋势（缺日补 0），统计逻辑拆分至 OrderStatsHelper。
     */
    @Override
    public ResponseDto<DailySalesVO> listDailySales(Integer merchantId, int days) {
        return orderStatsHelper.listDailySales(merchantId, days);
    }

    /**
     * 按关键字/订单号/创建时间区间分页查询订单，按创建时间与主键倒序返回。
     * scope 为顾客时追加 u_id 归属过滤。
     */
    @Override
    public ResponseDto<Order> queryOrder(OrderQueryRequest query, OrderScope scope) {
        Page<Order> page = new Page<>(query.getPageNo(), query.getPageSize());
        orderMapper.selectPageWithUserName(page, query.getKey(), query.getOrderNo(), query.getOrderStatus(),
                query.getCreateTimeStart(), query.getCreateTimeEnd(), scope.getOwnerUId());
        return ResponseDto.success(page);
    }

    /**
     * 统计各订单状态数量（-1/0/1/2/3 五个 key 缺省补 0），统计逻辑拆分至 OrderStatsHelper。
     */
    @Override
    public Map<String, Long> countByStatus(String key, String orderNo, Date createTimeStart,
                                           Date createTimeEnd, OrderScope scope) {
        return orderStatsHelper.countByStatus(key, orderNo, createTimeStart, createTimeEnd, scope);
    }

    /**
     * 按查询条件导出订单 Excel，逻辑拆分至 OrderExportService。
     */
    @Override
    public void export(String key, String orderNo, Date createTimeStart, Date createTimeEnd,
                       OrderScope scope, HttpServletResponse response) throws Exception {
        orderExportService.export(key, orderNo, createTimeStart, createTimeEnd, scope, response);
    }

    /**
     * 批量下单V2：
     * 1. 远程调用 sc-product 校验+扣减库存（库存不足抛错回滚）
     * 2. 远程调用 sc-user 拿收货地址快照
     * 3. 创建订单（含 orderNo/orderAmount/orderAddress/addressId）
     * 4. 批量写入订单商品中间表 t_order_item
     * 全程 @GlobalTransactional 由 Seata 保障跨服务最终一致。
     */
    @Override
    @GlobalTransactional
    public ResponseDto<Order> placeOrder(PlaceOrderRequest request) {
        validatePlaceRequest(request);
        // 入口分布式锁：防同一用户对同一批商品重复下单
        RLock lock = redissonClient.getLock(buildPlaceLockKey(request));
        boolean locked = false;
        try {
            locked = lock.tryLock(PLACE_LOCK_WAIT_SECONDS, PLACE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                return ResponseDto.error("下单处理中，请勿重复提交");
            }
            return orderPlaceHelper.doPlaceOrder(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseDto.error("下单加锁中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 下单入参基础校验，失败抛业务异常（由全局异常处理器转 error 返回）。
     */
    private void validatePlaceRequest(PlaceOrderRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("下单商品列表为空");
        }
        if (request.getUId() == null) {
            throw new BusinessException("用户ID不能为空");
        }
    }

    /**
     * 下单防重锁 key：用户ID + 本批商品ID集合的散列。
     */
    private String buildPlaceLockKey(PlaceOrderRequest request) {
        String itemsKey = request.getItems().stream()
                .map(PlaceOrderRequest.Item::getPId)
                .filter(Objects::nonNull)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return "lock:order:place:" + request.getUId() + ":" + itemsKey.hashCode();
    }

    /**
     * 鉴权 + 目标状态校验后走 CAS 更新。状态枚举：-1 取消 / 0 待付款 / 1 已下单 / 2 已完成。
     * 取消分支不同步调用 addStock，而是写入本地消息表 t_order_stock_restore_msg，
     * 由 StockRestoreMsgConsumer 异步消费回库存，避免 @GlobalTransactional 跨服务锁。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseDto<Order> updateStatus(Integer id, Integer orderStatus, OrderScope scope) {
        Order exists = loadOrderForStatusUpdate(id, orderStatus, scope);
        return doUpdateStatus(exists, orderStatus);
    }

    /**
     * 状态更新前置校验：目标状态合法性、订单存在性与归属鉴权，不通过抛业务异常。
     */
    private Order loadOrderForStatusUpdate(Integer id, Integer orderStatus, OrderScope scope) {
        if (id == null) {
            throw new BusinessException(MSG_ORDER_ID_REQUIRED);
        }
        // 已发货(3)必须走 ship 接口带快递信息，禁止通过本接口裸改状态
        if (SHIPPED_ORDER_STATUS.equals(orderStatus)) {
            throw new BusinessException("发货请通过发货接口提交快递信息");
        }
        if (!CANCEL_ORDER_STATUS.equals(orderStatus) && !PLACED_ORDER_STATUS.equals(orderStatus)
                && !COMPLETE_ORDER_STATUS.equals(orderStatus)) {
            throw new BusinessException("订单状态非法(-1:订单取消 1:已支付 2:已完成 3:已发货)");
        }
        Order exists = orderMapper.selectById(id);
        // 越权与不存在返回同一句提示，避免顾客探测他人订单是否存在
        if (exists == null || !scope.canManage(exists.getUId())) {
            throw new BusinessException(MSG_ORDER_NOT_FOUND);
        }
        // 支付后门加固：顾客不得直设已支付，0→1 只能由支付回调（PayServiceImpl）驱动
        if (PLACED_ORDER_STATUS.equals(orderStatus) && !scope.isUnrestricted()) {
            throw new BusinessException("请通过收银台完成支付");
        }
        return exists;
    }

    /**
     * 商家发货：仅商家/管理员可操作，1(已支付)→3(已发货)，CAS 同时写入快递公司/单号/发货时间。
     * 顾客侧确认收货(3→2)走 updateStatus 通用流转。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseDto<Order> ship(Integer id, String shippingCompany, String trackingNo, OrderScope scope) {
        Order exists = loadShippableOrder(id, shippingCompany, trackingNo, scope);
        if (SHIPPED_ORDER_STATUS.equals(exists.getOrderStatus())) {
            // 已发货视为幂等成功，避免商家重复点击报错
            return ResponseDto.success(null);
        }
        int rows = orderMapper.casShip(id, exists.getVersion(), shippingCompany.trim(), trackingNo.trim());
        if (rows == 0) {
            return ResponseDto.error(MSG_STATUS_CHANGED);
        }
        registerStatusMailAfterCommit(exists.getAddPerson(), exists.getOrderNo(), SHIPPED_ORDER_STATUS);
        return ResponseDto.success();
    }

    /**
     * 发货前置校验：入参、权限、存在性与当前状态（已支付或已发货），不通过抛业务异常。
     */
    private Order loadShippableOrder(Integer id, String shippingCompany, String trackingNo, OrderScope scope) {
        if (id == null) {
            throw new BusinessException(MSG_ORDER_ID_REQUIRED);
        }
        if (!StringUtils.hasText(shippingCompany) || !StringUtils.hasText(trackingNo)) {
            throw new BusinessException("快递公司与快递单号不能为空");
        }
        // 顾客无发货权限；商家/管理员/内部调用 scope 为 unrestricted
        if (!scope.isUnrestricted()) {
            throw new BusinessException("无权执行发货操作");
        }
        Order exists = orderMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException(MSG_ORDER_NOT_FOUND);
        }
        if (!PLACED_ORDER_STATUS.equals(exists.getOrderStatus())
                && !SHIPPED_ORDER_STATUS.equals(exists.getOrderStatus())) {
            throw new BusinessException("仅已支付订单可以发货");
        }
        return exists;
    }

    /**
     * 超时取消：读到订单后再确认一次仍是待付款(0)。
     * 顾客可能在延时消息到点前刚支付(0→1)，而状态机允许 1→-1，若不加这层判断会误杀已支付订单。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseDto<Order> cancelUnSubmitted(Integer id) {
        Order exists = id == null ? null : orderMapper.selectById(id);
        if (exists == null) {
            return ResponseDto.error(id == null ? MSG_ORDER_ID_REQUIRED : MSG_ORDER_NOT_FOUND);
        }
        if (!Objects.equals(exists.getOrderStatus(), UN_COMMIT_ORDER_STATUS)) {
            LOGGER.info("订单 {} 当前状态为 {}，非待付款，跳过超时取消", id, exists.getOrderStatus());
            return ResponseDto.success(null);
        }
        return doUpdateStatus(exists, CANCEL_ORDER_STATUS);
    }

    /**
     * 状态流转白名单：0→1 支付、0→-1 取消、1→3 发货、1→2 直接完成、1→-1 取消、3→2 确认收货。
     * 保留 1→2 —— 存量已支付订单无发货记录，且虚拟/无需物流商品允许商家直接完成。
     * 已发货(3)后不可取消，退款走售后工单。
     * 支付(0→1)不触碰库存 —— 库存在下单时已扣减。
     */
    private boolean isTransitionAllowed(Integer currentStatus, Integer targetStatus) {
        boolean allowed;
        if (Objects.equals(currentStatus, UN_COMMIT_ORDER_STATUS)) {
            allowed = PLACED_ORDER_STATUS.equals(targetStatus) || CANCEL_ORDER_STATUS.equals(targetStatus);
        } else if (Objects.equals(currentStatus, PLACED_ORDER_STATUS)) {
            allowed = SHIPPED_ORDER_STATUS.equals(targetStatus)
                    || COMPLETE_ORDER_STATUS.equals(targetStatus)
                    || CANCEL_ORDER_STATUS.equals(targetStatus);
        } else if (Objects.equals(currentStatus, SHIPPED_ORDER_STATUS)) {
            allowed = COMPLETE_ORDER_STATUS.equals(targetStatus);
        } else {
            allowed = false;
        }
        return allowed;
    }

    /**
     * 状态流转核心：状态机校验 → CAS 更新 → 取消时写回库存本地消息 → 提交后发通知邮件。
     * 调用方负责鉴权与目标状态合法性校验。
     */
    private ResponseDto<Order> doUpdateStatus(Order exists, Integer orderStatus) {
        // 已经是目标状态则视为幂等成功
        if (Objects.equals(exists.getOrderStatus(), orderStatus)) {
            return ResponseDto.success(null);
        }
        if (!isTransitionAllowed(exists.getOrderStatus(), orderStatus)) {
            return ResponseDto.error("当前订单状态不允许该操作");
        }
        return casUpdateAndNotify(exists, orderStatus);
    }

    /**
     * CAS 更新：version + 前置状态双重校验，并发下只有一个请求成功；
     * 成功后处理取消侧效应并在事务提交后发通知邮件（避免回滚后用户仍收到通知）。
     */
    private ResponseDto<Order> casUpdateAndNotify(Order exists, Integer orderStatus) {
        int rows = orderMapper.casUpdateStatus(
                exists.getOId(), exists.getOrderStatus(), orderStatus, exists.getVersion());
        if (rows == 0) {
            // 被其他请求抢先变更；幂等返回，不抛错以免前端误重试放大流量
            return ResponseDto.error(MSG_STATUS_CHANGED);
        }
        if (CANCEL_ORDER_STATUS.equals(orderStatus)) {
            registerCancelSideEffects(exists.getOId());
        }
        registerStatusMailAfterCommit(exists.getAddPerson(), exists.getOrderNo(), orderStatus);
        return ResponseDto.success();
    }

    /**
     * 取消侧效应：同事务写回库存本地消息 + 关闭在途支付单，提交后 best-effort 关网关侧交易单。
     */
    private void registerCancelSideEffects(Integer id) {
        // 同事务写入本地消息表，异步回库存；uk_o_id 唯一索引兜底重复取消
        OrderStockRestoreMsg msg = new OrderStockRestoreMsg();
        msg.setOId(id);
        msg.setStatus(0);
        msg.setRetryCnt(0);
        msg.setMaxRetry(STOCK_RESTORE_MAX_RETRY);
        msg.setNextRetry(new Date());
        try {
            orderStockRestoreMsgMapper.insert(msg);
        } catch (DuplicateKeyException dupEx) {
            // 消息已存在，说明已有取消任务在途，无需重复写
            LOGGER.info("订单 {} 回库存消息已存在，跳过写入", id, dupEx);
        }
        // 同事务关闭在途支付单（CAS 0→3）；支付回调若先赢 CAS，此处返回 null，
        // 而订单 CAS 也已失败返回，不会走到这里 —— 谁先 CAS 赢谁
        final PayRecord closedPay = payService.closePayForOrder(id);
        if (closedPay != null && closedPay.getTransactionId() != null) {
            // 提交后 best-effort 关网关侧交易单；失败无碍：本地已关，迟到回调会被拒
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    mockPayGatewayClient.closeTxn(closedPay.getTransactionId());
                }
            });
        }
    }

    /**
     * 事务提交成功后再发 MQ 邮件通知，避免回滚后用户仍收到通知。
     */
    private void registerStatusMailAfterCommit(final String addPerson, final String orderNo,
                                               final Integer orderStatus) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                toSendMail(addPerson, orderNo, orderStatus);
            }
        });
    }

    /**
     * 通过 MQ 发送订单状态变更邮件通知；发送失败仅记录日志，不影响主流程。
     * 支付(→1)不发信 —— 只有发货、取消与完成才需要通知。
     */
    private void toSendMail(String addPerson, String orderNo, Integer orderStatus) {
        if (PLACED_ORDER_STATUS.equals(orderStatus)) {
            return;
        }
        String subject;
        String message;
        if (CANCEL_ORDER_STATUS.equals(orderStatus)) {
            subject = "订单取消通知";
            message = "您好，编号为【" + orderNo + "】的订单已经取消，可前往系统进行查看";
        } else if (SHIPPED_ORDER_STATUS.equals(orderStatus)) {
            subject = "订单发货通知";
            message = "您好，编号为【" + orderNo + "】的订单已发货，可前往系统查看物流信息";
        } else {
            subject = "订单完成通知";
            message = "您好，编号为【" + orderNo + "】的订单已经完成，可前往系统进行查看";
        }
        OrderMessage orderMessage = new OrderMessage(addPerson, subject, message);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EXCHANGE_DIRECT,
                    RabbitMqConfig.ROUTING_KEY_EMAIL,
                    orderMessage
            );
            LOGGER.info("订单 {} 状态变更邮件通知已投递", orderNo);
        } catch (Exception e) {
            LOGGER.error("订单 {} 状态变更邮件通知投递失败", orderNo, e);
        }
    }

    /**
     * 按主键查询订单并关联查询订单商品明细，计算每个明细的小计金额。
     */
    @Override
    public Order getById(Serializable id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            return null;
        }
        // 关联查询订单商品明细，填入 orderItems 供前端展示
        try {
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOId, order.getOId()));
            fillSubtotal(order, items);
        } catch (Exception e) {
            LOGGER.warn("查询订单商品明细失败 oId={}", order.getOId(), e);
        }
        return order;
    }

    /**
     * 计算明细小计（单价×数量）并挂到订单上。
     */
    private void fillSubtotal(Order order, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (OrderItem it : items) {
            if (it.getPrice() != null && it.getQuantity() != null) {
                it.setSubtotal(it.getPrice().multiply(BigDecimal.valueOf(it.getQuantity())));
            }
        }
        order.setOrderItems(items);
    }

    /**
     * 按主键查询订单并做归属校验，无权时返回 null（与"不存在"同一表现，不暴露他人订单）。
     */
    @Override
    public Order getVisibleById(Integer id, OrderScope scope) {
        Order order = getById(id);
        if (order == null || !scope.canManage(order.getUId())) {
            return null;
        }
        return order;
    }

    /**
     * 按主键删除订单：校验存在性与归属，且仅允许删除终态订单。
     * 在途订单(0 待付款 / 1 已下单)不可删 —— 取消时写的回库存消息以 o_id 关联，
     * 订单被删掉那批库存就再也回不来了。
     */
    @Override
    public ResponseDto<Order> removeOrder(Integer id, OrderScope scope) {
        checkRemovable(id, scope);
        int rows = orderMapper.deleteById(id);
        return rows > 0 ? ResponseDto.success(null) : ResponseDto.error("删除订单失败");
    }

    /**
     * 删除前置校验：存在性、归属、终态、无在途售后，不通过抛业务异常。
     */
    private void checkRemovable(Integer id, OrderScope scope) {
        if (id == null) {
            throw new BusinessException(MSG_ORDER_ID_REQUIRED);
        }
        Order exists = orderMapper.selectById(id);
        if (exists == null || !scope.canManage(exists.getUId())) {
            throw new BusinessException(MSG_ORDER_NOT_FOUND);
        }
        Integer status = exists.getOrderStatus();
        if (!CANCEL_ORDER_STATUS.equals(status) && !COMPLETE_ORDER_STATUS.equals(status)) {
            throw new BusinessException("仅已取消或已完成的订单可以删除");
        }
        // 售后在途（待审核/退款中）的订单不可删：退款与库存回补都以 o_id 关联本单
        AfterSale inflight = afterSaleMapper.selectOne(
                new LambdaQueryWrapper<AfterSale>()
                        .eq(AfterSale::getOId, id)
                        .in(AfterSale::getStatus, AfterSale.STATUS_PENDING, AfterSale.STATUS_REFUNDING)
                        .last("LIMIT 1"));
        if (inflight != null) {
            throw new BusinessException("该订单存在处理中的售后申请，无法删除");
        }
    }

    /**
     * 秒杀下单同步段：参数校验 → 远程 Redis 原子预扣名额挡超卖 → 写 PENDING 结果到 Redis → 投递队列异步落库。
     * 预扣失败为终态 FAILED，预扣成功立即返回 PENDING 供前端轮询。
     */
    @Override
    public ResponseDto<SeckillResultVO> seckill(SeckillRequest request) {
        validateSeckillRequest(request);
        // 1. 远程 Redis 原子预扣名额（挡超卖、扛流量，失败不落库）。活动是否有效、是否在时间窗内由 sc-product 判定
        ResponseDto<SeckillActivity> pre =
                orderFeignService.seckillPreDeduct(request.getActivityId(), request.getuId());
        if (!OrderSupport.isSuccess(pre)) {
            String msg = pre == null ? "秒杀失败" : pre.getMsg();
            // 终态失败，无需轮询
            return ResponseDto.success(new SeckillResultVO("FAILED", msg, null));
        }
        // 2. 预扣成功 → 标记处理中并投递队列，异步落库
        SeckillResultVO pending = new SeckillResultVO("PENDING", "抢购成功，订单处理中", null);
        seckillOrderHelper.saveResult(request.getuId(), request.getActivityId(), pending);
        seckillOrderProducer.send(request);
        return ResponseDto.success(pending);
    }

    /**
     * 秒杀入参校验，不通过抛业务异常。
     */
    private void validateSeckillRequest(SeckillRequest request) {
        if (request == null || request.getuId() == null || request.getActivityId() == null) {
            throw new BusinessException("参数不能为空");
        }
        if (request.getAddressId() == null) {
            throw new BusinessException("请先维护默认收货地址");
        }
    }

    /**
     * 供前端轮询查询秒杀结果：从 Redis 读取 PENDING/SUCCESS/FAILED，无记录返回 NONE。
     */
    @Override
    public SeckillResultVO seckillResult(Integer uId, Integer activityId) {
        if (uId == null || activityId == null) {
            return new SeckillResultVO("FAILED", "参数不能为空", null);
        }
        SeckillResultVO vo = seckillOrderHelper.readResult(uId, activityId);
        // 结果已过期或从未发起
        return vo != null ? vo : new SeckillResultVO("NONE", "无秒杀记录", null);
    }

    /**
     * 秒杀落库异步段（由消费者调用）：在 Seata 全局事务下扣真实库存 + 创建订单 + 写订单商品明细。
     * 成功写 SUCCESS 结果；失败则补偿回滚 Redis 预扣并允许用户重试，再抛异常触发 Seata 全局回滚订单与真实库存。
     */
    @Override
    @GlobalTransactional
    public void processSeckillOrder(SeckillRequest req) {
        seckillOrderHelper.process(req);
    }
}
