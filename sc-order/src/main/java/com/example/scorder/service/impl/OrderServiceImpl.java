package com.example.scorder.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.*;
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
import com.example.scorder.vo.OrderExportVO;
import com.example.scorder.vo.OrderTimeoutVO;
import com.example.scorder.vo.SeckillResultVO;
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
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
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

    /** 秒杀结果 key 前缀：seckill:result:{uId}:{pId} */
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
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderStatus, 0)
                .orderByAsc(Order::getCreateTime);
        List<Order> orders = orderMapper.selectList(wrapper);
        List<OrderTimeoutVO> list = orders.stream().map(o -> {
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
        return ResponseDto.success(list);
    }


    /**
     * 按关键字/订单号/创建时间区间分页查询订单，按创建时间与主键倒序返回。
     */
    @Override
    public ResponseDto<Order> queryOrder(String key, String orderNo, Integer orderStatus, Date createTimeStart, Date createTimeEnd, int pageNo, int pageSize) {
        Page<Order> page = new Page<>(pageNo, pageSize);
        orderMapper.selectPageWithUserName(page, key, orderNo, orderStatus, createTimeStart, createTimeEnd);
        return ResponseDto.success(page);
    }

    /**
     * 统计各订单状态数量：无论是否有该状态订单，均返回 -1/0/1/2 四个 key，缺省为 0。
     */
    @Override
    public Map<String, Long> countByStatus(String key, String orderNo, Date createTimeStart, Date createTimeEnd) {
        Map<String, Long> result = new HashMap<>();
        result.put(String.valueOf(UN_COMMIT_ORDER_STATUS), 0L);
        result.put(String.valueOf(PLACED_ORDER_STATUS), 0L);
        result.put(String.valueOf(COMPLETE_ORDER_STATUS), 0L);
        result.put(String.valueOf(CANCEL_ORDER_STATUS), 0L);
        List<Map<String, Object>> rows = orderMapper.countGroupByStatus(key, orderNo, createTimeStart, createTimeEnd);
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
    public void export(String key, String orderNo, Date createTimeStart, Date createTimeEnd, HttpServletResponse response) throws Exception {
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<Order>()
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
     * 批量下单（旧版）：远程扣减库存 + 本地写订单，订单金额为商品价格求和。
     * 全程在 Seata 全局事务下，远程扣库存失败直接返回错误。
     */
    @Override
    @GlobalTransactional
    public ResponseDto<Order> placeOrder(List<Product> products, String addPerson) {
        if (products == null || products.isEmpty()) {
            return ResponseDto.error("下单商品列表为空");
        }
        // 远程调用扣减库存
        ResponseDto<Product> deduct = orderFeignService.deductStock(products);
        if (deduct != null && deduct.getCode() != null && deduct.getCode() != 200) {
            return ResponseDto.error(deduct.getMsg() == null ? "扣减库存失败" : deduct.getMsg());
        }
        // 创建订单
        Order order = new Order();
        order.setAddPerson(addPerson == null || addPerson.isEmpty() ? "anonymous" : addPerson);
        order.setCreateTime(new Date());
        order.setProductList(products);
        order.setOrderStatus(1);
        order.setOrderNo(generateOrderNo());
        // 订单金额：所选商品价格求和（Product.price 为 Integer）
        BigDecimal amount = products.stream()
                .filter(p -> p != null && p.getPrice() != null)
                .map(p -> BigDecimal.valueOf(p.getPrice().doubleValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setOrderAmount(amount);
        orderMapper.insert(order);
        return ResponseDto.success(null);
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
        // 1. 组装扣库存入参，并把前端传的 price/quantity 同步过去
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

        // 2. 拉取收货地址快照
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

        // 3. 创建订单
        Order order = new Order();
        order.setAddPerson(request.getAddPerson() == null || request.getAddPerson().isEmpty()
                ? "anonymous" : request.getAddPerson());
        order.setCreateTime(new Date());
        order.setOrderStatus(request.getOrderStatus());
        order.setOrderNo(generateOrderNo());
        order.setOrderAddress(orderAddress);

        // 订单金额：sum(price * quantity)
        BigDecimal amount = BigDecimal.ZERO;
        for (PlaceOrderRequest.Item it : request.getItems()) {
            BigDecimal price = it.getPrice() == null ? BigDecimal.ZERO
                    : BigDecimal.valueOf(it.getPrice().doubleValue());
            int qty = it.getQuantity() == null ? 0 : it.getQuantity();
            amount = amount.add(price.multiply(BigDecimal.valueOf(qty)));
        }
        order.setOrderAmount(amount);
        orderMapper.insert(order);

        // 4. 写订单商品中间表
        List<OrderItem> itemRecords = new ArrayList<>();
        for (PlaceOrderRequest.Item it : request.getItems()) {
            OrderItem oi = new OrderItem();
            oi.setOId(order.getOId());
            oi.setPId(it.getPId());
            oi.setQuantity(it.getQuantity());
            oi.setPrice(it.getPrice() == null ? null
                    : BigDecimal.valueOf(it.getPrice().doubleValue()));
            itemRecords.add(oi);
        }
        // 批量补商品名称快照
        List<Integer> pIds = new ArrayList<>();
        for (PlaceOrderRequest.Item it : request.getItems()) pIds.add(it.getPId());
        try {
            ResponseDto<Product> prodResp = orderFeignService.listByIds(pIds);
            if (prodResp != null && prodResp.getCode() != null && prodResp.getCode() == 200
                    && prodResp.getDataList() != null) {
                Map<Integer, String> nameMap = new HashMap<>();
                for (Product p : prodResp.getDataList()) {
                    if (p.getPId() != null) nameMap.put(p.getPId(), p.getPName());
                }
                for (OrderItem oi : itemRecords) {
                    oi.setPName(nameMap.get(oi.getPId()));
                }
            }
        } catch (Exception e) {
            log.warn("拉取商品名称快照失败, err={}", e.getMessage());
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
     * 校验订单状态合法后基于 version CAS 更新状态。状态枚举：0 取消 / 1 已下单 / 2 已完成。
     * 状态机：仅允许 1 已下单 → 0 取消、1 已下单 → 2 已完成。
     * 取消分支不再同步调用 addStock，而是写入本地消息表 t_order_stock_restore_msg，
     * 由 StockRestoreMsgConsumer 异步消费回库存，避免 @GlobalTransactional 跨服务锁。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseDto<Order> updateStatus(Integer id, Integer orderStatus) {
        if (id == null) {
            return ResponseDto.error("订单ID不能为空");
        }
        // 目标状态只允许 取消/已完成；传 1(已下单) 无意义且会误发通知邮件
        if (!CANCEL_ORDER_STATUS.equals(orderStatus) && !COMPLETE_ORDER_STATUS.equals(orderStatus)) {
            return ResponseDto.error("订单状态非法(0:订单取消 2:已完成)");
        }
        Order exists = orderMapper.selectById(id);
        if (exists == null) {
            return ResponseDto.error("订单不存在");
        }
        Integer currentStatus = exists.getOrderStatus();
        // 状态机校验：「已下单(1)」可流转到「取消」或「已完成」；「未提交(0)」超时可流转到「取消」
        boolean allowed = Objects.equals(currentStatus, PLACED_ORDER_STATUS)
                || (Objects.equals(currentStatus, UN_COMMIT_ORDER_STATUS) && CANCEL_ORDER_STATUS.equals(orderStatus));
        if (!allowed) {
            // 已经是目标状态则视为幂等成功
            if (Objects.equals(currentStatus, orderStatus)) {
                return ResponseDto.success(null);
            }
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
     */
    private void toSendMail(String addPerson, String orderNo, Integer orderStatus) {
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
     * 按主键删除订单，删除前校验订单是否存在。
     */
    @Override
    public ResponseDto<Order> removeOrder(Integer id) {
        if (id == null) {
            return ResponseDto.error("订单ID不能为空");
        }
        Order exists = orderMapper.selectById(id);
        if (exists == null) {
            return ResponseDto.error("订单不存在");
        }
        int rows = orderMapper.deleteById(id);
        return rows > 0 ? ResponseDto.success(null) : ResponseDto.error("删除订单失败");
    }

    /**
     * 秒杀下单同步段：参数校验 → 远程 Redis 原子预扣库存挡超卖 → 写 PENDING 结果到 Redis → 投递队列异步落库。
     * 预扣失败为终态 FAILED，预扣成功立即返回 PENDING 供前端轮询。
     */
    @Override
    public ResponseDto<SeckillResultVO> seckill(SeckillRequest request) {
        if (request == null || request.getuId() == null || request.getpId() == null) {
            return ResponseDto.error("参数不能为空");
        }
        if (request.getAddressId() == null) {
            return ResponseDto.error("请先维护默认收货地址");
        }
        // 1. 远程 Redis 原子预扣库存（挡超卖、扛流量，失败不落库）
        ResponseDto<Product> pre = orderFeignService.seckillPreDeduct(request.getpId(), request.getuId());
        if (pre == null || pre.getCode() == null || pre.getCode() != 200) {
            String msg = pre == null ? "秒杀失败" : pre.getMsg();
            // 终态失败，无需轮询
            return ResponseDto.success(new SeckillResultVO("FAILED", msg, null));
        }
        // 2. 预扣成功 → 标记处理中并投递队列，异步落库
        SeckillResultVO pending = new SeckillResultVO("PENDING", "抢购成功，订单处理中", null);
        saveSeckillResult(request.getuId(), request.getpId(), pending);
        seckillOrderProducer.send(request);
        return ResponseDto.success(pending);
    }

    /**
     * 供前端轮询查询秒杀结果：从 Redis 读取 PENDING/SUCCESS/FAILED，无记录返回 NONE。
     */
    @Override
    public SeckillResultVO seckillResult(Integer uId, Integer pId) {
        if (uId == null || pId == null) {
            return new SeckillResultVO("FAILED", "参数不能为空", null);
        }
        RBucket<SeckillResultVO> bucket = redissonClient.getBucket(SECKILL_RESULT_KEY + uId + ":" + pId);
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
        Integer pId = req.getpId();
        try {
            // 1. 扣真实库存（数量1），条件更新兜底防超卖
            Product item = new Product();
            item.setPId(pId);
            item.setStock(1);
            ResponseDto<Product> deduct = orderFeignService.checkAndDeductStock(
                    Collections.singletonList(item));
            if (deduct == null || deduct.getCode() == null || deduct.getCode() != 200) {
                throw new RuntimeException(deduct == null ? "扣减库存失败" : deduct.getMsg());
            }

            // 2. 商品价格/名称快照
            Integer price = null;
            String pName = null;
            ResponseDto<Product> prodResp = orderFeignService.listByIds(
                    Collections.singletonList(pId));
            if (prodResp != null && prodResp.getCode() != null && prodResp.getCode() == 200
                    && prodResp.getDataList() != null && !prodResp.getDataList().isEmpty()) {
                Product p = prodResp.getDataList().get(0);
                price = p.getPrice();
                pName = p.getPName();
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

            // 4. 创建订单
            Order order = new Order();
            order.setAddPerson(req.getAddPerson() == null || req.getAddPerson().isEmpty()
                    ? "anonymous" : req.getAddPerson());
            order.setCreateTime(new Date());
            order.setOrderStatus(1);
            order.setOrderNo(generateOrderNo());
            order.setOrderAddress(orderAddress);
            order.setOrderAmount(price == null ? BigDecimal.ZERO : BigDecimal.valueOf(price.doubleValue()));
            orderMapper.insert(order);

            // 5. 订单商品明细（数量1）
            OrderItem oi = new OrderItem();
            oi.setOId(order.getOId());
            oi.setPId(pId);
            oi.setPName(pName);
            oi.setQuantity(1);
            oi.setPrice(price == null ? null : BigDecimal.valueOf(price.doubleValue()));
            orderItemMapper.insert(oi);

            // 6. 成功结果（Redis 写入不受 Seata 回滚影响）
            saveSeckillResult(uId, pId, new SeckillResultVO("SUCCESS", "下单成功", order.getOrderNo()));
        } catch (Exception e) {
            log.error("[seckill] 落库失败，执行补偿 uId={}, pId={}", uId, pId, e);
            // 补偿：回滚 Redis 预扣库存 + 移除已购标记，允许用户重试
            try {
                orderFeignService.rollbackSeckillStock(pId, uId);
            } catch (Exception ex) {
                log.error("[seckill] 补偿回滚 Redis 失败 uId={}, pId={}", uId, pId, ex);
            }
            saveSeckillResult(uId, pId, new SeckillResultVO("FAILED", "下单失败，请重试", null));
            // 抛出以触发 Seata 全局回滚（订单/真实库存）
            throw new RuntimeException("秒杀落库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将秒杀结果写入 Redis，有效期 1 小时，供前端轮询查询。
     */
    private void saveSeckillResult(Integer uId, Integer pId, SeckillResultVO vo) {
        RBucket<SeckillResultVO> bucket = redissonClient.getBucket(SECKILL_RESULT_KEY + uId + ":" + pId);
        bucket.set(vo, 1, TimeUnit.HOURS);
    }
}
