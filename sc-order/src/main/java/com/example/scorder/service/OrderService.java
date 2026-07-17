package com.example.scorder.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.Order;
import com.curry.model.Product;
import com.example.scorder.dto.PlaceOrderRequest;
import com.example.scorder.dto.SeckillRequest;
import com.example.scorder.vo.SeckillResultVO;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

public interface OrderService extends IService<Order> {
    /**
     * 演示链路：创建订单并通过 Feign 调用 sc-product 创建商品，全程在 Seata 全局事务下，
     * 触发异常会引发跨服务全局回滚。
     */
    ResponseDto<Order> addOrder();

    /**
     * 分页查询订单
     */
    ResponseDto<Order> queryOrder(String key, String orderNo, Date createTimeStart, Date createTimeEnd, int pageNo, int pageSize);

    /**
     * 批量下单（旧版）：扣减所选商品库存并创建订单
     */
    ResponseDto<Order> placeOrder(List<Product> products, String addPerson);

    /**
     * 批量下单（新版）：校验库存 → 扣减库存 → 写订单 → 写订单商品中间表
     */
    ResponseDto<Order> placeOrder(PlaceOrderRequest request);

    /**
     * 更新订单状态(0:订单取消 1:已下单 2:已完成)
     */
    ResponseDto<Order> updateStatus(Integer id, Integer orderStatus);

    /**
     * 根据ID删除订单
     */
    ResponseDto<Order> removeOrder(Integer id);

    /**
     * 按查询条件导出订单列表为 Excel（EasyExcel）
     */
    void export(String key, String orderNo, Date createTimeStart, Date createTimeEnd, HttpServletResponse response) throws Exception;

    /**
     * 秒杀下单（同步段）：远程 Redis 预扣库存 → 成功则投递队列异步落库，立即返回。
     * @return status=PENDING 表示预扣成功、订单处理中（前端轮询）；status=FAILED 为终态失败
     */
    ResponseDto<SeckillResultVO> seckill(SeckillRequest request);

    /**
     * 查询秒杀结果（供前端轮询）。
     */
    SeckillResultVO seckillResult(Integer uId, Integer pId);

    /**
     * 秒杀落库（异步段，消费者调用）：Seata 全局事务下创建订单+扣真实库存，失败则补偿回滚 Redis 预扣。
     */
    void processSeckillOrder(SeckillRequest message);
}
