package com.example.scorder.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.curry.model.Order;
import com.curry.model.Product;
import com.example.scorder.config.OrderConfig;
import com.example.scorder.dto.PlaceOrderRequest;
import com.example.scorder.dto.SeckillRequest;
import com.example.scorder.service.OrderService;
import com.example.scorder.vo.SeckillResultVO;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping(value = "/order")
public class OrderController {
    @Autowired
    private OrderConfig orderConfig;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private OrderService orderService;
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

    @PostMapping("/createOrder")
    public ResponseDto<Order> createOrder() {
        return orderService.addOrder();
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable("id") Integer id) {
        return orderService.getById(id);
    }

    @GetMapping("/list")
    public List<Order> list() {
        return orderService.list();
    }


    @GetMapping("/queryOrder")
    @SentinelResource(value = "order-queryOrder",blockHandler = "queryOrderBlockHandler")
    public ResponseDto<Order> queryOrder(@RequestParam(value = "key", required = false) String key,
                                         @RequestParam(value = "orderNo", required = false) String orderNo,
                                         @RequestParam(value = "createTimeStart", required = false)
                                         @DateTimeFormat(pattern = "yyyy-MM-dd") Date createTimeStart,
                                         @RequestParam(value = "createTimeEnd", required = false)
                                         @DateTimeFormat(pattern = "yyyy-MM-dd") Date createTimeEnd,
                                         @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                         @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return orderService.queryOrder(key, orderNo, createTimeStart, createTimeEnd, pageNo, pageSize);
    }


    /**
     * 兜底方法：参数列表须与原方法一致，末尾追加 BlockException，否则 Sentinel 匹配不到
     */
    public ResponseDto<Order> queryOrderBlockHandler(String key, String orderNo,
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
        orderService.export(key, orderNo, createTimeStart, createTimeEnd, response);
    }

    /**
     * 批量下单
     */
    @PostMapping("/placeOrder")
    public ResponseDto<Order> placeOrder(@RequestBody List<Product> products,
                                         @RequestParam(value = "addPerson", required = false) String addPerson) {
        return orderService.placeOrder(products, addPerson);
    }

    /**
     * 批量下单V2：校验+扣库存+订单+订单商品中间表
     */
    @PostMapping("/placeOrderV2")
    public ResponseDto<Order> placeOrderV2(@RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(request);
    }

    /**
     * 秒杀下单：Redis 预扣 → 异步落库，立即返回结果状态
     */
    @PostMapping("/seckill")
    public ResponseDto<SeckillResultVO> seckill(@RequestBody SeckillRequest request) {
        return orderService.seckill(request);
    }

    /**
     * 查询秒杀结果（前端轮询用）
     */
    @GetMapping("/seckill/result")
    public ResponseDto<SeckillResultVO> seckillResult(@RequestParam("uId") Integer uId,
                                                      @RequestParam("pId") Integer pId) {
        return ResponseDto.success(orderService.seckillResult(uId, pId));
    }

    @PutMapping
    public boolean update(@RequestBody Order order) {
        return orderService.updateById(order);
    }

    @PostMapping("/updateStatus")
    public ResponseDto<Order> updateStatus(@RequestParam("id") Integer id,
                                           @RequestParam("orderStatus") Integer orderStatus) {
        return orderService.updateStatus(id, orderStatus);
    }

    @DeleteMapping("/{id}")
    public ResponseDto<Order> delete(@PathVariable("id") Integer id) {
        return orderService.removeOrder(id);
    }
}
