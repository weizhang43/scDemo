package com.example.scorder.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.Order;
import com.curry.model.Product;
import com.example.scorder.auth.OrderScope;
import com.example.scorder.dto.PlaceOrderRequest;
import com.example.scorder.dto.SeckillRequest;
import com.example.scorder.vo.MonthlySalesVO;
import com.example.scorder.vo.OrderTimeoutVO;
import com.example.scorder.vo.ProductSalesRankVO;
import com.example.scorder.vo.SeckillResultVO;
import com.example.scorder.vo.TypeSalesVO;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface OrderService extends IService<Order> {
    /**
     * 首页预警：查询状态为 0 的订单，附带到期时间（createTime + 超时时长）供前端倒计时。
     */
    ResponseDto<OrderTimeoutVO> listTimeoutWarning();

    /**
     * 顾客首页：只查当前登录人（t_order.add_person 存的是 uName）状态为 0 的即将超期订单。
     */
    ResponseDto<OrderTimeoutVO> listMyTimeoutWarning(String uName);

    /**
     * 顾客首页：商品销量榜，销量由 t_order_item 聚合，价格与图片经 Feign 从 sc-product 补齐。
     */
    ResponseDto<ProductSalesRankVO> listSalesRank(int limit);

    /**
     * 统计报表：销量按商品类型分组（仅统计已下单/已完成订单）。
     * merchantId 非 null 时只统计该商家商品与公共商品。
     */
    ResponseDto<TypeSalesVO> listTypeSales(Integer merchantId);

    /**
     * 统计报表：近三个自然月（含当月）每月销量，无销量的月份补 0。
     */
    ResponseDto<MonthlySalesVO> listMonthlySales(Integer merchantId);


    /**
     * 分页查询订单。scope 决定归属过滤：顾客只返回自己的订单。
     */
    ResponseDto<Order> queryOrder(String key, String orderNo, Integer orderStatus, Date createTimeStart, Date createTimeEnd, int pageNo, int pageSize, OrderScope scope);

    /**
     * 统计各订单状态数量（用于列表状态 Tab 徽标），返回 key 为状态值字符串、value 为数量。
     */
    Map<String, Long> countByStatus(String key, String orderNo, Date createTimeStart, Date createTimeEnd, OrderScope scope);

    /**
     * 按主键查询订单及商品明细，scope 无权时返回 null（不区分"不存在"与"无权"）。
     */
    Order getVisibleById(Integer id, OrderScope scope);

    /**
     * 批量下单（新版）：校验库存 → 扣减库存 → 写订单 → 写订单商品中间表
     */
    ResponseDto<Order> placeOrder(PlaceOrderRequest request);

    /**
     * 更新订单状态(-1:订单取消 1:已下单/已支付 2:已完成)，scope 无权时按"订单不存在"拒绝。
     */
    ResponseDto<Order> updateStatus(Integer id, Integer orderStatus, OrderScope scope);

    /**
     * 商家发货：1(已支付)→3(已发货)，同时写入快递公司/单号/发货时间。顾客无权调用。
     */
    ResponseDto<Order> ship(Integer id, String shippingCompany, String trackingNo, OrderScope scope);

    /**
     * 超时取消：仅当订单仍处于待付款(0)时才流转到取消。
     * 供 MQ 延时消息与定时任务使用 —— 顾客可能已经支付(0→1)，那条延时消息不能再把订单取消掉。
     */
    ResponseDto<Order> cancelUnSubmitted(Integer id);

    /**
     * 根据ID删除订单。scope 无权时按"订单不存在"拒绝；仅允许删除终态(-1/2)订单。
     */
    ResponseDto<Order> removeOrder(Integer id, OrderScope scope);

    /**
     * 按查询条件导出订单列表为 Excel（EasyExcel）
     */
    void export(String key, String orderNo, Date createTimeStart, Date createTimeEnd, OrderScope scope, HttpServletResponse response) throws Exception;

    /**
     * 秒杀下单（同步段）：远程 Redis 预扣活动名额 → 成功则投递队列异步落库，立即返回。
     * @return status=PENDING 表示预扣成功、订单处理中（前端轮询）；status=FAILED 为终态失败
     */
    ResponseDto<SeckillResultVO> seckill(SeckillRequest request);

    /**
     * 查询秒杀结果（供前端轮询）。
     */
    SeckillResultVO seckillResult(Integer uId, Integer activityId);

    /**
     * 秒杀落库（异步段，消费者调用）：Seata 全局事务下创建订单+扣真实库存，失败则补偿回滚 Redis 预扣。
     */
    void processSeckillOrder(SeckillRequest message);
}
