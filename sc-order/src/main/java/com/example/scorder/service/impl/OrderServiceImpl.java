package com.example.scorder.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.*;
import com.example.scorder.auth.OrderScope;
import com.example.scorder.config.RabbitMqConfig;
import com.example.scorder.dto.PlaceOrderRequest;
import com.example.scorder.dto.SeckillRequest;
import com.example.scorder.entity.OrderStockRestoreMsg;
import com.example.scorder.mapper.OrderItemMapper;
import com.example.scorder.mapper.OrderMapper;
import com.example.scorder.mapper.OrderStockRestoreMsgMapper;
import com.example.scorder.mq.SeckillOrderProducer;
import com.example.scorder.service.OrderFeignService;
import com.example.scorder.service.OrderService;
import com.example.scorder.service.UserFeignService;
import com.example.scorder.vo.MonthlySalesVO;
import com.example.scorder.vo.OrderExportVO;
import com.example.scorder.vo.OrderTimeoutVO;
import com.example.scorder.vo.ProductSalesRankVO;
import com.example.scorder.vo.SeckillResultVO;
import com.example.scorder.vo.TypeSalesVO;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Autowired
    private OrderFeignService orderFeignService;

    @Autowired
    private UserFeignService userFeignService;

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
    private RabbitTemplate rabbitTemplate;

    /** 秒杀结果 key 前缀：seckill:result:{uId}:{activityId} */
    private static final String SECKILL_RESULT_KEY = "seckill:result:";

    /**
     * 订单取消状态码
     */
    public static final Integer CANCEL_ORDER_STATUS = -1;
    /** 待签收状态码 */
    public static final Integer PLACED_ORDER_STATUS = 1;
    /** 已完成状态码 */
    public static final Integer COMPLETE_ORDER_STATUS = 2;
    /** 待付款 */
    public static final Integer UN_COMMIT_ORDER_STATUS = 0;

    /** 订单超时时长（分钟）：状态为0的订单以 createTime + 该时长作为倒计时到期点 */
    @Value("${order-timeout-minute:30}")
    private Integer orderTimeOutMinute;

    /**
     * 演示链路：本地创建订单 + Feign 远程创建商品，外加一次 1/0 异常。
     * 通过 @GlobalTransactional 让 Seata 协调跨服务事务，远程调用失败会触发全局回滚。
     */
    @Override
    @GlobalTransactional //开启事务
    public ResponseDto<Order> addOrder() {
        Order order = new Order();
        order.setAddPerson("curry");
        order.setCreateTime(new Date());
        order.setOrderStatus(1);
        order.setOrderNo(generateOrderNo());
        //创建订单
        orderMapper.insert(order);
        //远程调用添加商品
        orderFeignService.createProduct();

        int a = 1/0;

        return ResponseDto.success(null);

    }

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
        return orders.stream().map(o -> {
            OrderTimeoutVO vo = new OrderTimeoutVO();
            vo.setOId(o.getOId());
            vo.setOrderNo(o.getOrderNo());
            vo.setOrderAmount(o.getOrderAmount());
            vo.setOrderStatus(o.getOrderStatus());
            vo.setCreateTime(o.getCreateTime());
            if (o.getCreateTime() != null) {
                vo.setExpireTime(new Date(o.getCreateTime().getTime() + orderTimeOutMinute * 60L * 1000L));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public ResponseDto<ProductSalesRankVO> listSalesRank(int limit) {
        List<ProductSalesRankVO> rank = new ArrayList<>();
        for (Map<String, Object> row : orderItemMapper.selectSalesRank(limit)) {
            ProductSalesRankVO vo = new ProductSalesRankVO();
            vo.setPId(row.get("pId") == null ? null : ((Number) row.get("pId")).intValue());
            vo.setPName((String) row.get("pName"));
            vo.setSalesCount(row.get("salesCount") == null ? 0L : ((Number) row.get("salesCount")).longValue());
            rank.add(vo);
        }
        if (rank.isEmpty()) {
            // 传空列表会生成 IN ()，直接短路
            return ResponseDto.success(rank);
        }
        // 补当前价格与图片；补不到就只展示名称+销量，不因下游异常让整个榜单失败
        try {
            List<Integer> pIds = rank.stream().map(ProductSalesRankVO::getPId).collect(Collectors.toList());
            ResponseDto<Product> prodResp = orderFeignService.listSellableByIds(pIds);
            if (prodResp != null && prodResp.getCode() != null && prodResp.getCode() == 200
                    && prodResp.getDataList() != null) {
                Map<Integer, Product> prodMap = new HashMap<>();
                for (Product p : prodResp.getDataList()) {
                    if (p.getPId() != null) prodMap.put(p.getPId(), p);
                }
                // listSellableByIds 只返回上架商品，查不到即已下架 —— 顾客首页要看不到
                List<ProductSalesRankVO> sellableRank = new ArrayList<>();
                for (ProductSalesRankVO vo : rank) {
                    Product p = prodMap.get(vo.getPId());
                    if (p == null) continue;
                    vo.setPrice(p.getPrice());
                    vo.setImageUrl(p.getImageUrl());
                    vo.setDiscount(p.getDiscount());
                    vo.setEffectivePrice(effectivePriceOf(p));
                    if (p.getPName() != null) vo.setPName(p.getPName());
                    sellableRank.add(vo);
                }
                rank = sellableRank;
            }
        } catch (Exception e) {
            log.warn("[salesRank] 拉取商品价格/图片失败, err={}", e.getMessage());
        }
        return ResponseDto.success(rank);
    }

    @Override
    public ResponseDto<TypeSalesVO> listTypeSales(Integer merchantId) {
        return ResponseDto.success(orderItemMapper.selectTypeSales(merchantId));
    }

    /**
     * 折线图断月会误导趋势，SQL 只返回有销量的月份，这里按近三个自然月骨架补 0。
     */
    @Override
    public ResponseDto<MonthlySalesVO> listMonthlySales(Integer merchantId) {
        Map<String, Long> salesByMonth = new HashMap<>();
        for (MonthlySalesVO vo : orderItemMapper.selectMonthlySales(merchantId)) {
            salesByMonth.put(vo.getMonth(), vo.getSalesCount() == null ? 0L : vo.getSalesCount());
        }
        List<MonthlySalesVO> result = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = 2; i >= 0; i--) {
            String month = now.minusMonths(i).toString();
            result.add(new MonthlySalesVO(month, salesByMonth.getOrDefault(month, 0L)));
        }
        return ResponseDto.success(result);
    }


    /**
     * 按关键字/订单号/创建时间区间分页查询订单，按创建时间与主键倒序返回。
     * scope 为顾客时追加 u_id 归属过滤。
     */
    @Override
    public ResponseDto<Order> queryOrder(String key, String orderNo, Integer orderStatus, Date createTimeStart, Date createTimeEnd, int pageNo, int pageSize, OrderScope scope) {
        Page<Order> page = new Page<>(pageNo, pageSize);
        orderMapper.selectPageWithUserName(page, key, orderNo, orderStatus, createTimeStart, createTimeEnd, scope.getOwnerUId());
        return ResponseDto.success(page);
    }

    /**
     * 统计各订单状态数量：无论是否有该状态订单，均返回 -1/0/1/2 四个 key，缺省为 0。
     */
    @Override
    public Map<String, Long> countByStatus(String key, String orderNo, Date createTimeStart, Date createTimeEnd, OrderScope scope) {
        Map<String, Long> result = new HashMap<>();
        result.put(String.valueOf(UN_COMMIT_ORDER_STATUS), 0L);
        result.put(String.valueOf(PLACED_ORDER_STATUS), 0L);
        result.put(String.valueOf(COMPLETE_ORDER_STATUS), 0L);
        result.put(String.valueOf(CANCEL_ORDER_STATUS), 0L);
        List<Map<String, Object>> rows = orderMapper.countGroupByStatus(key, orderNo, createTimeStart, createTimeEnd, scope.getOwnerUId());
        for (Map<String, Object> row : rows) {
            Object status = row.get("orderStatus");
            Object cnt = row.get("cnt");
            if (status == null) {
                continue;
            }
            result.put(String.valueOf(status), cnt == null ? 0L : ((Number) cnt).longValue());
        }
        return result;
    }

    /**
     * 按查询条件导出订单 Excel：使用 EasyExcel 直接写入 HttpServletResponse 输出流。
     * @param response Servlet 响应，文件名以 UTF-8 编码避免中文乱码
     */
    @Override
    public void export(String key, String orderNo, Date createTimeStart, Date createTimeEnd, OrderScope scope, HttpServletResponse response) throws Exception {
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<Order>()
                .eq(scope.getOwnerUId() != null, Order::getUId, scope.getOwnerUId())
                .like(key != null && !key.isEmpty(), Order::getAddPerson, key)
                .like(orderNo != null && !orderNo.isEmpty(), Order::getOrderNo, orderNo)
                .ge(createTimeStart != null, Order::getCreateTime, createTimeStart)
                .le(createTimeEnd != null, Order::getCreateTime, createTimeEnd)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getOId);
        List<Order> list = orderMapper.selectList(queryWrapper);
        List<OrderExportVO> rows = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            rows.add(OrderExportVO.of(list.get(i), i + 1));
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("订单列表", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), OrderExportVO.class)
                .sheet("订单列表")
                .doWrite(rows);
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
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseDto.error("下单商品列表为空");
        }
        if (request.getUId() == null) {
            return ResponseDto.error("用户ID不能为空");
        }

        // 入口分布式锁：防同一用户对同一批商品重复下单
        String itemsKey = request.getItems().stream()
                .map(PlaceOrderRequest.Item::getPId)
                .filter(Objects::nonNull)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String lockKey = "lock:order:place:" + request.getUId() + ":" + itemsKey.hashCode();
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 30, TimeUnit.SECONDS);
            if (!locked) {
                return ResponseDto.error("下单处理中，请勿重复提交");
            }
            return doPlaceOrder(request);
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
     * 实际下单逻辑：扣库存 → 拉地址快照 → 写订单 → 批量写订单商品中间表，附带商品名称快照。
     * 任一步失败由 Seata 全局回滚保证最终一致。
     */
    private ResponseDto<Order> doPlaceOrder(PlaceOrderRequest request) {
        // 1. 先拉服务端权威商品信息：已下架/已删除的直接拒单，同时拿到折后价与名称快照
        List<Integer> pIds = new ArrayList<>();
        for (PlaceOrderRequest.Item it : request.getItems()) {
            pIds.add(it.getPId());
        }
        Map<Integer, Product> sellable = new HashMap<>();
        ResponseDto<Product> prodResp = orderFeignService.listSellableByIds(pIds);
        if (prodResp != null && prodResp.getCode() != null && prodResp.getCode() == 200
                && prodResp.getDataList() != null) {
            for (Product p : prodResp.getDataList()) {
                if (p.getPId() != null) sellable.put(p.getPId(), p);
            }
        }
        for (PlaceOrderRequest.Item it : request.getItems()) {
            Product p = sellable.get(it.getPId());
            if (p == null) {
                return ResponseDto.error("商品已下架或不存在，无法下单");
            }
            // 前端展示价与服务端有效价不一致：促销恰在下单瞬间开始/结束，拒单让用户看到新价格
            if (it.getExpectedPrice() != null
                    && it.getExpectedPrice().compareTo(effectivePriceOf(p)) != 0) {
                return ResponseDto.error("商品「" + p.getPName() + "」价格已更新，请刷新后重新下单");
            }
        }

        // 2. 组装扣库存入参
        List<Product> deductItems = new ArrayList<>();
        for (PlaceOrderRequest.Item it : request.getItems()) {
            Product p = new Product();
            p.setPId(it.getPId());
            p.setStock(it.getQuantity());
            deductItems.add(p);
        }
        ResponseDto<Product> deduct = orderFeignService.checkAndDeductStock(deductItems);
        if (deduct == null || deduct.getCode() == null || deduct.getCode() != 200) {
            String msg = deduct == null ? "扣减库存失败" : deduct.getMsg();
            return ResponseDto.error(msg == null ? "扣减库存失败" : msg);
        }

        // 3. 拉取收货地址快照
        String orderAddress = "";
        if (request.getAddressId() != null) {
            try {
                ResponseDto<Address> addrResp = userFeignService.getAddress(request.getAddressId());
                if (addrResp != null && addrResp.getCode() != null && addrResp.getCode() == 200
                        && addrResp.getDaoResult() != null) {
                    Address addr = JSON.parseObject(JSON.toJSONString(addrResp.getDaoResult()),Address.class);
                    orderAddress = buildAddressText(addr);
                }
            } catch (Exception e) {
                log.warn("拉取收货地址失败 addressId={}, err={}", request.getAddressId(), e.getMessage());
            }
        }

        // 4. 创建订单
        Order order = new Order();
        order.setAddPerson(request.getAddPerson() == null || request.getAddPerson().isEmpty()
                ? "anonymous" : request.getAddPerson());
        order.setUId(request.getUId());
        order.setCreateTime(new Date());
        order.setOrderStatus(request.getOrderStatus());
        order.setOrderNo(generateOrderNo());
        order.setOrderAddress(orderAddress);

        // 订单金额：sum(服务端有效价 * quantity)，不采信前端传来的价格
        BigDecimal amount = BigDecimal.ZERO;
        for (PlaceOrderRequest.Item it : request.getItems()) {
            BigDecimal price = effectivePriceOf(sellable.get(it.getPId()));
            int qty = it.getQuantity() == null ? 0 : it.getQuantity();
            amount = amount.add(price.multiply(BigDecimal.valueOf(qty)));
        }
        order.setOrderAmount(amount);
        orderMapper.insert(order);

        // 5. 写订单商品中间表，价格与名称均取服务端快照
        List<OrderItem> itemRecords = new ArrayList<>();
        for (PlaceOrderRequest.Item it : request.getItems()) {
            Product p = sellable.get(it.getPId());
            OrderItem oi = new OrderItem();
            oi.setOId(order.getOId());
            oi.setPId(it.getPId());
            oi.setQuantity(it.getQuantity());
            oi.setPrice(effectivePriceOf(p));
            oi.setPName(p.getPName());
            itemRecords.add(oi);
        }
        if (!itemRecords.isEmpty()) {
            orderItemMapper.insertBatch(itemRecords);
        }
        if(UN_COMMIT_ORDER_STATUS.compareTo(request.getOrderStatus()) == 0){
            //未提交订单，三分钟不提交自动超时
            //redisTemplate.opsForValue().set("orderExpired:"+order.getOId(),"1",30*60,TimeUnit.SECONDS);
            //todo 使用消息队列实现订单超时
            rabbitTemplate.convertAndSend("",RabbitMqConfig.QUEUE_ORDER,order.getOId());
        }


        return ResponseDto.success(order);
    }

    /**
     * 商品的服务端有效单价：sc-product 已回填折后价则用它，否则退回原价。
     */
    private BigDecimal effectivePriceOf(Product p) {
        if (p == null) {
            return BigDecimal.ZERO;
        }
        if (p.getEffectivePrice() != null) {
            return p.getEffectivePrice();
        }
        return p.getPrice() == null ? BigDecimal.ZERO : BigDecimal.valueOf(p.getPrice());
    }

    /**
     * 把地址对象拼接为单行文本：收件人 + 电话 + 省市区 + 详情。
     */
    private String buildAddressText(Address addr) {
        StringBuilder sb = new StringBuilder();
        if (addr.getConsignee() != null) sb.append(addr.getConsignee()).append(' ');
        if (addr.getPhone() != null) sb.append(addr.getPhone()).append(' ');
        if (addr.getProvince() != null) sb.append(addr.getProvince());
        if (addr.getCity() != null) sb.append(addr.getCity());
        if (addr.getDistrict() != null) sb.append(addr.getDistrict());
        if (addr.getDetail() != null) sb.append(addr.getDetail());
        return sb.toString().trim();
    }

    /**
     * 生成订单编号：ORD + yyyyMMdd + 4位流水号(当日下单数量+1)
     * 流水号基于"当日已存在的订单数 + 1"。
     * 注意：高并发下可能存在并发冲突，需配合唯一索引兜底重试。
     */
    private String generateOrderNo() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dayPrefix = sdf.format(new Date());
        int count = orderMapper.countByDay(dayPrefix);
        int seq = count + 1;
        DecimalFormat df = new DecimalFormat("0000");
        return "ORD" + dayPrefix + df.format(seq);
    }

    /**
     * 鉴权 + 目标状态校验后走 CAS 更新。状态枚举：-1 取消 / 0 待付款 / 1 已下单 / 2 已完成。
     * 取消分支不同步调用 addStock，而是写入本地消息表 t_order_stock_restore_msg，
     * 由 StockRestoreMsgConsumer 异步消费回库存，避免 @GlobalTransactional 跨服务锁。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseDto<Order> updateStatus(Integer id, Integer orderStatus, OrderScope scope) {
        if (id == null) {
            return ResponseDto.error("订单ID不能为空");
        }
        if (!CANCEL_ORDER_STATUS.equals(orderStatus)
                && !PLACED_ORDER_STATUS.equals(orderStatus)
                && !COMPLETE_ORDER_STATUS.equals(orderStatus)) {
            return ResponseDto.error("订单状态非法(-1:订单取消 1:已支付 2:已完成)");
        }
        Order exists = orderMapper.selectById(id);
        if (exists == null) {
            return ResponseDto.error("订单不存在");
        }
        // 越权与不存在返回同一句提示，避免顾客探测他人订单是否存在
        if (!scope.canManage(exists.getUId())) {
            return ResponseDto.error("订单不存在");
        }
        return doUpdateStatus(exists, orderStatus);
    }

    /**
     * 超时取消：读到订单后再确认一次仍是待付款(0)。
     * 顾客可能在延时消息到点前刚支付(0→1)，而状态机允许 1→-1，若不加这层判断会误杀已支付订单。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseDto<Order> cancelUnSubmitted(Integer id) {
        if (id == null) {
            return ResponseDto.error("订单ID不能为空");
        }
        Order exists = orderMapper.selectById(id);
        if (exists == null) {
            return ResponseDto.error("订单不存在");
        }
        if (!Objects.equals(exists.getOrderStatus(), UN_COMMIT_ORDER_STATUS)) {
            log.info("订单 {} 当前状态为 {}，非待付款，跳过超时取消", id, exists.getOrderStatus());
            return ResponseDto.success(null);
        }
        return doUpdateStatus(exists, CANCEL_ORDER_STATUS);
    }

    /**
     * 状态流转白名单：0→1 支付、0→-1 取消、1→2 完成、1→-1 取消，其余一律拒绝。
     * 支付(0→1)不触碰库存 —— 库存在下单时已扣减。
     */
    private boolean isTransitionAllowed(Integer currentStatus, Integer targetStatus) {
        if (Objects.equals(currentStatus, UN_COMMIT_ORDER_STATUS)) {
            return PLACED_ORDER_STATUS.equals(targetStatus) || CANCEL_ORDER_STATUS.equals(targetStatus);
        }
        if (Objects.equals(currentStatus, PLACED_ORDER_STATUS)) {
            return COMPLETE_ORDER_STATUS.equals(targetStatus) || CANCEL_ORDER_STATUS.equals(targetStatus);
        }
        return false;
    }

    /**
     * 状态流转核心：状态机校验 → CAS 更新 → 取消时写回库存本地消息 → 提交后发通知邮件。
     * 调用方负责鉴权与目标状态合法性校验。
     */
    private ResponseDto<Order> doUpdateStatus(Order exists, Integer orderStatus) {
        Integer id = exists.getOId();
        Integer currentStatus = exists.getOrderStatus();
        // 已经是目标状态则视为幂等成功
        if (Objects.equals(currentStatus, orderStatus)) {
            return ResponseDto.success(null);
        }
        if (!isTransitionAllowed(currentStatus, orderStatus)) {
            return ResponseDto.error("当前订单状态不允许该操作");
        }
        // CAS 更新：version + 前置状态双重校验，并发下只有一个请求成功
        int rows = orderMapper.casUpdateStatus(id, currentStatus, orderStatus, exists.getVersion());
        if (rows == 0) {
            // 被其他请求抢先变更；幂等返回，不抛错以免前端误重试放大流量
            return ResponseDto.error("订单状态已变更，请刷新后重试");
        }
        if (CANCEL_ORDER_STATUS.equals(orderStatus)) {
            // 同事务写入本地消息表，异步回库存；uk_o_id 唯一索引兜底重复取消
            OrderStockRestoreMsg msg = new OrderStockRestoreMsg();
            msg.setOId(id);
            msg.setStatus(0);
            msg.setRetryCnt(0);
            msg.setMaxRetry(5);
            msg.setNextRetry(new Date());
            try {
                orderStockRestoreMsgMapper.insert(msg);
            } catch (DuplicateKeyException dupEx) {
                // 消息已存在，说明已有取消任务在途，无需重复写
                log.info("订单 {} 回库存消息已存在，跳过写入", id);
            }
        }
        //事务提交成功后再发 MQ 邮件通知，避免回滚后用户仍收到通知
        final String addPerson = exists.getAddPerson();
        final String orderNo = exists.getOrderNo();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                toSendMail(addPerson, orderNo, orderStatus);
            }
        });

        return ResponseDto.success();
    }

    /**
     * 通过 MQ 发送订单状态变更邮件通知；发送失败仅记录日志，不影响主流程。
     * 支付(→1)不发信 —— 只有取消与完成才需要通知。
     */
    private void toSendMail(String addPerson, String orderNo, Integer orderStatus) {
        if (PLACED_ORDER_STATUS.equals(orderStatus)) {
            return;
        }
        String subject = "订单完成通知";
        String message = "您好，编号为【" + orderNo + "】的订单已经完成，可前往系统进行查看";
        if (CANCEL_ORDER_STATUS.equals(orderStatus)) {
            subject = "订单取消通知";
            message = "您好，编号为【" + orderNo + "】的订单已经取消，可前往系统进行查看";
        }
        OrderMessage orderMessage = new OrderMessage(addPerson, subject, message);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EXCHANGE_DIRECT,
                    RabbitMqConfig.ROUTING_KEY_EMAIL,
                    orderMessage
            );
            log.info("订单 {} 状态变更邮件通知已投递", orderNo);
        } catch (Exception e) {
            log.error("订单 {} 状态变更邮件通知投递失败", orderNo, e);
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
            if (items != null && !items.isEmpty()) {
                for (OrderItem it : items) {
                    if (it.getPrice() != null && it.getQuantity() != null) {
                        it.setSubtotal(it.getPrice().multiply(BigDecimal.valueOf(it.getQuantity())));
                    }
                }
                order.setOrderItems(items);
            }
        } catch (Exception e) {
            log.warn("查询订单商品明细失败 oId={}, err={}", order.getOId(), e.getMessage());
        }
        return order;
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
        if (id == null) {
            return ResponseDto.error("订单ID不能为空");
        }
        Order exists = orderMapper.selectById(id);
        if (exists == null || !scope.canManage(exists.getUId())) {
            return ResponseDto.error("订单不存在");
        }
        Integer status = exists.getOrderStatus();
        if (!CANCEL_ORDER_STATUS.equals(status) && !COMPLETE_ORDER_STATUS.equals(status)) {
            return ResponseDto.error("仅已取消或已完成的订单可以删除");
        }
        int rows = orderMapper.deleteById(id);
        return rows > 0 ? ResponseDto.success(null) : ResponseDto.error("删除订单失败");
    }

    /**
     * 秒杀下单同步段：参数校验 → 远程 Redis 原子预扣名额挡超卖 → 写 PENDING 结果到 Redis → 投递队列异步落库。
     * 预扣失败为终态 FAILED，预扣成功立即返回 PENDING 供前端轮询。
     */
    @Override
    public ResponseDto<SeckillResultVO> seckill(SeckillRequest request) {
        if (request == null || request.getuId() == null || request.getActivityId() == null) {
            return ResponseDto.error("参数不能为空");
        }
        if (request.getAddressId() == null) {
            return ResponseDto.error("请先维护默认收货地址");
        }
        // 1. 远程 Redis 原子预扣名额（挡超卖、扛流量，失败不落库）。活动是否有效、是否在时间窗内由 sc-product 判定
        ResponseDto<SeckillActivity> pre =
                orderFeignService.seckillPreDeduct(request.getActivityId(), request.getuId());
        if (pre == null || pre.getCode() == null || pre.getCode() != 200) {
            String msg = pre == null ? "秒杀失败" : pre.getMsg();
            // 终态失败，无需轮询
            return ResponseDto.success(new SeckillResultVO("FAILED", msg, null));
        }
        // 2. 预扣成功 → 标记处理中并投递队列，异步落库
        SeckillResultVO pending = new SeckillResultVO("PENDING", "抢购成功，订单处理中", null);
        saveSeckillResult(request.getuId(), request.getActivityId(), pending);
        seckillOrderProducer.send(request);
        return ResponseDto.success(pending);
    }

    /**
     * 供前端轮询查询秒杀结果：从 Redis 读取 PENDING/SUCCESS/FAILED，无记录返回 NONE。
     */
    @Override
    public SeckillResultVO seckillResult(Integer uId, Integer activityId) {
        if (uId == null || activityId == null) {
            return new SeckillResultVO("FAILED", "参数不能为空", null);
        }
        RBucket<SeckillResultVO> bucket = redissonClient.getBucket(SECKILL_RESULT_KEY + uId + ":" + activityId);
        SeckillResultVO vo = bucket.get();
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
        Integer uId = req.getuId();
        Integer activityId = req.getActivityId();
        // 真实库存不足时不归还名额：名额本就是从库存里划出的上限，
        // 库存已被正常销售抽干说明这批名额是幽灵名额，归还只会让下一个人撞同一堵墙
        boolean stockShortage = false;
        try {
            // 1. 活动快照：秒杀价与商品名都取服务端的，绝不采信请求里的价格
            ResponseDto<SeckillActivity> actResp = orderFeignService.getSeckillActivity(activityId);
            if (actResp == null || actResp.getCode() == null || actResp.getCode() != 200
                    || actResp.getDaoResult() == null) {
                throw new RuntimeException("秒杀活动不存在");
            }
            SeckillActivity activity = JSON.parseObject(
                    JSON.toJSONString(actResp.getDaoResult()), SeckillActivity.class);
            if (activity.getSeckillPrice() == null) {
                throw new RuntimeException("秒杀价缺失");
            }
            Integer pId = activity.getPId();

            // 2. 扣真实库存（数量1），条件更新兜底防超卖
            Product item = new Product();
            item.setPId(pId);
            item.setStock(1);
            ResponseDto<Product> deduct = orderFeignService.checkAndDeductStock(
                    Collections.singletonList(item));
            if (deduct == null || deduct.getCode() == null || deduct.getCode() != 200) {
                stockShortage = true;
                throw new RuntimeException(deduct == null ? "扣减库存失败" : deduct.getMsg());
            }

            // 3. 收货地址快照
            String orderAddress = "";
            try {
                ResponseDto<Address> addrResp = userFeignService.getAddress(req.getAddressId());
                if (addrResp != null && addrResp.getCode() != null && addrResp.getCode() == 200
                        && addrResp.getDaoResult() != null) {
                    Address addr = JSON.parseObject(JSON.toJSONString(addrResp.getDaoResult()), Address.class);
                    orderAddress = buildAddressText(addr);
                }
            } catch (Exception e) {
                log.warn("秒杀拉取收货地址失败 addressId={}, err={}", req.getAddressId(), e.getMessage());
            }

            // 4. 创建订单，金额为秒杀价
            Order order = new Order();
            order.setAddPerson(req.getAddPerson() == null || req.getAddPerson().isEmpty()
                    ? "anonymous" : req.getAddPerson());
            order.setUId(uId);
            order.setCreateTime(new Date());
            order.setOrderStatus(1);
            order.setOrderNo(generateOrderNo());
            order.setOrderAddress(orderAddress);
            order.setOrderAmount(activity.getSeckillPrice());
            orderMapper.insert(order);

            // 5. 订单商品明细（数量1）
            OrderItem oi = new OrderItem();
            oi.setOId(order.getOId());
            oi.setPId(pId);
            oi.setPName(activity.getPName());
            oi.setQuantity(1);
            oi.setPrice(activity.getSeckillPrice());
            orderItemMapper.insert(oi);

            // 6. 成功结果（Redis 写入不受 Seata 回滚影响）
            saveSeckillResult(uId, activityId, new SeckillResultVO("SUCCESS", "下单成功", order.getOrderNo()));
        } catch (Exception e) {
            log.error("[seckill] 落库失败，执行补偿 uId={}, activityId={}", uId, activityId, e);
            // 补偿：移除已购标记允许重试；名额是否归还取决于失败原因
            try {
                orderFeignService.rollbackSeckillStock(activityId, uId, !stockShortage);
            } catch (Exception ex) {
                log.error("[seckill] 补偿回滚 Redis 失败 uId={}, activityId={}", uId, activityId, ex);
            }
            saveSeckillResult(uId, activityId, new SeckillResultVO("FAILED",
                    stockShortage ? "商品库存不足，秒杀失败" : "下单失败，请重试", null));
            // 抛出以触发 Seata 全局回滚（订单/真实库存）
            throw new RuntimeException("秒杀落库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将秒杀结果写入 Redis，有效期 1 小时，供前端轮询查询。
     */
    private void saveSeckillResult(Integer uId, Integer activityId, SeckillResultVO vo) {
        RBucket<SeckillResultVO> bucket = redissonClient.getBucket(SECKILL_RESULT_KEY + uId + ":" + activityId);
        bucket.set(vo, 1, TimeUnit.HOURS);
    }
}
