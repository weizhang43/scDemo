package com.example.scorder.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.curry.model.Order;
import com.curry.model.Product;
import com.curry.model.annotation.OpLog;
import com.curry.model.auth.AuthConstant;
import com.example.scorder.auth.OrderScopeResolver;
import com.example.scorder.config.OrderConfig;
import com.example.scorder.dto.PlaceOrderRequest;
import com.example.scorder.dto.SeckillRequest;
import com.example.scorder.service.OrderService;
import com.example.scorder.vo.SeckillResultVO;
import com.google.common.collect.Lists;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(value = "/order")
public class OrderController {
    @Autowired
    private OrderConfig orderConfig;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedissonClient redissonClient;

    /** 首页预警：状态为0的订单，带到期时间供倒计时 */
    @GetMapping("/warning/timeout")
    public ResponseDto<com.example.scorder.vo.OrderTimeoutVO> timeoutWarning() {
        return orderService.listTimeoutWarning();
    }

    /** 顾客首页：只看自己的即将超期订单，下单人取网关注入的用户名 */
    @GetMapping("/warning/timeout/mine")
    public ResponseDto<com.example.scorder.vo.OrderTimeoutVO> myTimeoutWarning(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_NAME, required = false) String uName) {
        return orderService.listMyTimeoutWarning(uName);
    }

    /** 顾客首页/商家工作台：商品销量榜。商家身份只统计自己的商品与公共商品 */
    @GetMapping("/rank/sales")
    public ResponseDto<com.example.scorder.vo.ProductSalesRankVO> salesRank(
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        return orderService.listSalesRank(Math.min(Math.max(limit, 1), 50), merchantIdOf(uId, uType));
    }

    /** 首页工作台概览：今日成交额/单量、待发货、待付款、待处理售后。商家按明细口径，管理员按平台实付口径 */
    @GetMapping("/statistics/overview")
    public ResponseDto<com.example.scorder.vo.DashboardOverviewVO> dashboardOverview(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        return orderService.dashboardOverview(merchantIdOf(uId, uType));
    }

    /** 首页工作台：近 N 天（默认 7，上限 30）逐日成交趋势，无成交日期补 0 */
    @GetMapping("/statistics/dailySales")
    public ResponseDto<com.example.scorder.vo.DailySalesVO> dailySales(
            @RequestParam(value = "days", defaultValue = "7") int days,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        return orderService.listDailySales(merchantIdOf(uId, uType), Math.min(Math.max(days, 1), 30));
    }

    /** 统计报表：销量按商品类型分组。商家只统计自己的商品与公共商品，管理员/内部调用查全量 */
    @GetMapping("/statistics/typeSales")
    public ResponseDto<com.example.scorder.vo.TypeSalesVO> typeSales(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        return orderService.listTypeSales(merchantIdOf(uId, uType));
    }

    /** 统计报表：近三个自然月（含当月）每月销量，无销量的月份补 0 */
    @GetMapping("/statistics/monthlySales")
    public ResponseDto<com.example.scorder.vo.MonthlySalesVO> monthlySales(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        return orderService.listMonthlySales(merchantIdOf(uId, uType));
    }

    /** 仅商家身份返回其商家 ID 用于统计过滤；管理员/内部调用返回 null 查全量 */
    private static Integer merchantIdOf(Integer uId, Integer uType) {
        return uType != null && uType == AuthConstant.U_TYPE_MERCHANT ? uId : null;
    }

    /** requestId 幂等键 TTL */
    private static final long IDEM_TTL_SECONDS = 30;

    /**
     * 演示接口：组装一个订单并通过 RestTemplate 直接拉取商品信息塞入 productList 后返回。
     */
    @GetMapping("/getOrder/{id}")
    public Order getOrderInfo(@PathVariable("id") int id){
        Order order = new Order();
        order.setOId(id);
        order.setAddPerson("curry"+orderConfig.getOrderType());
        String productUrl = "http://localhost:8002/sc-product/product/getProduct";
        Product product = restTemplate.getForEntity(productUrl, Product.class).getBody();
        order.setProductList(Arrays.asList(product));
        return order;
    }

    /**
     * 按主键查询订单。顾客访问他人订单时返回 null（200 空 body），前端落到"未找到订单信息"。
     */
    @GetMapping("/{id}")
    public Order get(@PathVariable("id") Integer id) {
        return orderService.getVisibleById(id, OrderScopeResolver.current());
    }


    /**
     * 分页查询订单：支持关键字、订单号、创建时间区间过滤。
     * 被 Sentinel 资源 order-queryOrder 限流，触发限流时走 queryOrderBlockHandler 返回兜底数据。
     * 方法签名不能变 —— blockHandler 要求参数列表逐一匹配，scope 只能在方法体内解析。
     */
    @GetMapping("/queryOrder")
    @SentinelResource(value = "order-queryOrder",blockHandler = "queryOrderBlockHandler")
    public ResponseDto<Order> queryOrder(@RequestParam(value = "key", required = false) String key,
                                         @RequestParam(value = "orderNo", required = false) String orderNo,
                                         @RequestParam(value = "orderStatus", required = false) Integer orderStatus,
                                         @RequestParam(value = "createTimeStart", required = false)
                                         @DateTimeFormat(pattern = "yyyy-MM-dd") Date createTimeStart,
                                         @RequestParam(value = "createTimeEnd", required = false)
                                         @DateTimeFormat(pattern = "yyyy-MM-dd") Date createTimeEnd,
                                         @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                         @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return orderService.queryOrder(key, orderNo, orderStatus, createTimeStart, createTimeEnd, pageNo, pageSize,
                OrderScopeResolver.current());
    }

    /**
     * 统计各订单状态数量（列表状态 Tab 徽标用），过滤条件与 queryOrder 一致但不含状态本身。
     */
    @GetMapping("/statusCount")
    public ResponseDto<Map<String, Long>> statusCount(@RequestParam(value = "key", required = false) String key,
                                                      @RequestParam(value = "orderNo", required = false) String orderNo,
                                                      @RequestParam(value = "createTimeStart", required = false)
                                                      @DateTimeFormat(pattern = "yyyy-MM-dd") Date createTimeStart,
                                                      @RequestParam(value = "createTimeEnd", required = false)
                                                      @DateTimeFormat(pattern = "yyyy-MM-dd") Date createTimeEnd) {
        return ResponseDto.success(orderService.countByStatus(key, orderNo, createTimeStart, createTimeEnd,
                OrderScopeResolver.current()));
    }


    /**
     * 兜底方法：参数列表须与原方法一致，末尾追加 BlockException，否则 Sentinel 匹配不到
     */
    public ResponseDto<Order> queryOrderBlockHandler(String key, String orderNo, Integer orderStatus,
                                         Date createTimeStart, Date createTimeEnd,
                                         int pageNo, int pageSize, BlockException ex) {
        Page<Order> page = new Page<>();
        page.setCurrent(pageNo);
        page.setSize(pageSize);
        page.setTotal(1);
        Order order = new Order();
        order.setOrderNo("test-order-001");
        order.setOrderAmount(new BigDecimal(100));
        order.setAddPerson("curry");
        order.setOrderAddress("测试地址");
        order.setCreateTime(new Date());
        page.setRecords(Lists.newArrayList(order));

        return ResponseDto.success(page);

    }

    /**
     * 按查询条件导出订单列表（EasyExcel）
     */
    @GetMapping("/export")
    public void export(@RequestParam(value = "key", required = false) String key,
                       @RequestParam(value = "orderNo", required = false) String orderNo,
                       @RequestParam(value = "createTimeStart", required = false)
                       @DateTimeFormat(pattern = "yyyy-MM-dd") Date createTimeStart,
                       @RequestParam(value = "createTimeEnd", required = false)
                       @DateTimeFormat(pattern = "yyyy-MM-dd") Date createTimeEnd,
                       HttpServletResponse response) throws Exception {
        orderService.export(key, orderNo, createTimeStart, createTimeEnd, OrderScopeResolver.current(), response);
    }

    /**
     * 批量下单V2：校验+扣库存+订单+订单商品中间表
     */
    @OpLog(module = "订单管理", type = OpLog.OpType.ADD, description = "批量下单V2")
    @PostMapping("/placeOrderV2")
    public ResponseDto<Order> placeOrderV2(@RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(request);
    }

    /**
     * 秒杀下单：Redis 预扣活动名额 → 异步落库，立即返回结果状态
     */
    @OpLog(module = "订单管理", type = OpLog.OpType.ADD, description = "秒杀下单")
    @PostMapping("/seckill")
    public ResponseDto<SeckillResultVO> seckill(@RequestBody SeckillRequest request) {
        return orderService.seckill(request);
    }

    /**
     * 查询秒杀结果（前端轮询用）
     */
    @GetMapping("/seckill/result")
    public ResponseDto<SeckillResultVO> seckillResult(@RequestParam("uId") Integer uId,
                                                      @RequestParam("activityId") Integer activityId) {
        return ResponseDto.success(orderService.seckillResult(uId, activityId));
    }

    /**
     * 按订单主键更新订单信息。
     */
    @PutMapping
    public boolean update(@RequestBody Order order) {
        return orderService.updateById(order);
    }

    /**
     * 更新订单状态（如待支付/已支付/已取消等流转）。
     * requestId 可选：前端传则用 Redis SETNX 做幂等去重，30s 内同 requestId 视为重复提交。
     */
    @OpLog(module = "订单管理", type = OpLog.OpType.UPDATE, description = "更新订单状态")
    @PostMapping("/updateStatus")
    public ResponseDto<Order> updateStatus(@RequestParam("id") Integer id,
                                           @RequestParam("orderStatus") Integer orderStatus,
                                           @RequestParam(value = "requestId", required = false) String requestId) {
        if (StringUtils.hasText(requestId)) {
            String key = "idem:updateStatus:" + requestId;
            boolean ok = redissonClient.getBucket(key).trySet("1", IDEM_TTL_SECONDS, TimeUnit.SECONDS);
            if (!ok) {
                return ResponseDto.error("请求正在处理，请勿重复提交");
            }
        }
        return orderService.updateStatus(id, orderStatus, OrderScopeResolver.current());
    }

    /**
     * 商家发货：填写快递公司与单号，订单 1(已支付)→3(已发货)。
     */
    @OpLog(module = "订单管理", type = OpLog.OpType.UPDATE, description = "订单发货")
    @PostMapping("/ship")
    public ResponseDto<Order> ship(@RequestParam("id") Integer id,
                                   @RequestParam("shippingCompany") String shippingCompany,
                                   @RequestParam("trackingNo") String trackingNo) {
        return orderService.ship(id, shippingCompany, trackingNo, OrderScopeResolver.current());
    }

    /**
     * 按主键删除订单（逻辑/物理删除由 Service 决定）。
     */
    @OpLog(module = "订单管理", type = OpLog.OpType.DELETE, description = "删除订单")
    @DeleteMapping("/{id}")
    public ResponseDto<Order> delete(@PathVariable("id") Integer id) {
        return orderService.removeOrder(id, OrderScopeResolver.current());
    }
}
