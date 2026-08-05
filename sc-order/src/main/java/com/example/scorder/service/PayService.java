package com.example.scorder.service;

import com.example.scorder.auth.OrderScope;
import com.example.scorder.entity.PayRecord;
import com.example.scorder.vo.PayCreateVO;
import com.example.scorder.vo.PayStatusVO;
import response.ResponseDto;

import java.util.Map;

public interface PayService {

    /**
     * 创建支付单：归属校验 → 每订单一张在途单（幂等复用）→ 调网关预下单拿 transactionId。
     */
    ResponseDto<PayCreateVO> createPay(Integer oId, String channel, OrderScope scope);

    /**
     * 按支付单号查状态（前端轮询），归属校验不通过按不存在处理。
     */
    ResponseDto<PayStatusVO> getStatus(String payNo, OrderScope scope);

    /**
     * 处理网关异步回调：验签 → 防重放 → 金额核对 → CAS 推进支付单与订单。
     * 每次回调无条件落一条 t_pay_notify_log。
     *
     * @return "success" 表示网关无需重试，"fail" 要求网关按退避重发
     */
    String handleNotify(Map<String, String> params);

    /**
     * 订单取消时关闭在途支付单（CAS 0→3），须在订单取消的同一事务内调用。
     * 只操作支付单表，不回调订单，避免与 OrderService 循环依赖。
     *
     * @return 被关闭的支付单，无在途单或已被抢先推进时返回 null
     */
    PayRecord closePayForOrder(Integer oId);
}
