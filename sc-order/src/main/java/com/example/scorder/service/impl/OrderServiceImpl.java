package com.example.scorder.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Address;
import com.curry.model.Order;
import com.curry.model.OrderItem;
import com.curry.model.Product;
import com.example.scorder.dto.PlaceOrderRequest;
import com.example.scorder.dto.SeckillRequest;
import com.example.scorder.mapper.OrderItemMapper;
import com.example.scorder.mapper.OrderMapper;
import com.example.scorder.mq.SeckillOrderProducer;
import com.example.scorder.service.OrderFeignService;
import com.example.scorder.service.OrderService;
import com.example.scorder.service.UserFeignService;
import com.example.scorder.vo.OrderExportVO;
import com.example.scorder.vo.SeckillResultVO;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
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

    /** 秒杀结果 key 前缀：seckill:result:{uId}:{pId} */
    private static final String SECKILL_RESULT_KEY = "seckill:result:";

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
    public ResponseDto<Order> queryOrder(String key, String orderNo, Date createTimeStart, Date createTimeEnd, int pageNo, int pageSize) {
        Page<Order> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<Order>()
                .like(key != null && !key.isEmpty(), Order::getAddPerson, key)
                .like(orderNo != null && !orderNo.isEmpty(), Order::getOrderNo, orderNo)
                .ge(createTimeStart != null, Order::getCreateTime, createTimeStart)
                .le(createTimeEnd != null, Order::getCreateTime, createTimeEnd)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getOId);
        page = orderMapper.selectPage(page, queryWrapper);
        return ResponseDto.success(page);
    }

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
        if (request.getuId() == null) {
            return ResponseDto.error("用户ID不能为空");
        }

        // 入口分布式锁：防同一用户对同一批商品重复下单
        String itemsKey = request.getItems().stream()
                .map(PlaceOrderRequest.Item::getpId)
                .filter(Objects::nonNull)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String lockKey = "lock:order:place:" + request.getuId() + ":" + itemsKey.hashCode();
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

    private ResponseDto<Order> doPlaceOrder(PlaceOrderRequest request) {
        // 1. 组装扣库存入参，并把前端传的 price/quantity 同步过去
        List<Product> deductItems = new ArrayList<>();
        for (PlaceOrderRequest.Item it : request.getItems()) {
            Product p = new Product();
            p.setPId(it.getpId());
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
        order.setOrderStatus(1);
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
            oi.setPId(it.getpId());
            oi.setQuantity(it.getQuantity());
            oi.setPrice(it.getPrice() == null ? null
                    : BigDecimal.valueOf(it.getPrice().doubleValue()));
            itemRecords.add(oi);
        }
        // 批量补商品名称快照
        List<Integer> pIds = new ArrayList<>();
        for (PlaceOrderRequest.Item it : request.getItems()) pIds.add(it.getpId());
        try {
            ResponseDto<Product> prodResp = orderFeignService.listByIds(pIds);
            if (prodResp != null && prodResp.getCode() != null && prodResp.getCode() == 200
                    && prodResp.getDataList() != null) {
                java.util.Map<Integer, String> nameMap = new java.util.HashMap<>();
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

        return ResponseDto.success(order);
    }

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
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd");
        String dayPrefix = sdf.format(new Date());
        int count = orderMapper.countByDay(dayPrefix);
        int seq = count + 1;
        java.text.DecimalFormat df = new java.text.DecimalFormat("0000");
        return "ORD" + dayPrefix + df.format(seq);
    }

    @Override
    public ResponseDto<Order> updateStatus(Integer id, Integer orderStatus) {
        if (id == null) {
            return ResponseDto.error("订单ID不能为空");
        }
        if (orderStatus == null || orderStatus < 0 || orderStatus > 2) {
            return ResponseDto.error("订单状态非法(0:订单取消 1:已下单 2:已完成)");
        }
        Order exists = orderMapper.selectById(id);
        if (exists == null) {
            return ResponseDto.error("订单不存在");
        }
        Order update = new Order();
        update.setOId(id);
        update.setOrderStatus(orderStatus);
        int rows = orderMapper.updateById(update);
        return rows > 0 ? ResponseDto.success(null) : ResponseDto.error("更新订单状态失败");
    }

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
                    java.util.Collections.singletonList(item));
            if (deduct == null || deduct.getCode() == null || deduct.getCode() != 200) {
                throw new RuntimeException(deduct == null ? "扣减库存失败" : deduct.getMsg());
            }

            // 2. 商品价格/名称快照
            Integer price = null;
            String pName = null;
            ResponseDto<Product> prodResp = orderFeignService.listByIds(
                    java.util.Collections.singletonList(pId));
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

    private void saveSeckillResult(Integer uId, Integer pId, SeckillResultVO vo) {
        RBucket<SeckillResultVO> bucket = redissonClient.getBucket(SECKILL_RESULT_KEY + uId + ":" + pId);
        bucket.set(vo, 1, TimeUnit.HOURS);
    }
}
