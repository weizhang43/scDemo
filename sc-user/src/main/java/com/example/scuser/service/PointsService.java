package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.PointsRecord;
import response.ResponseDto;

import java.util.Map;

/**
 * 会员积分服务：余额/等级汇总、明细、支付后加分（幂等）、积分兑换优惠券。
 */
public interface PointsService extends IService<PointsRecord> {

    /**
     * 积分汇总：余额、累计获取、当前等级与下一等级信息、可兑换选项。
     */
    Map<String, Object> summary(Integer uId);

    /**
     * 积分明细分页。
     */
    ResponseDto<PointsRecord> pageRecords(Integer uId, Integer pageNum, Integer pageSize);

    /**
     * 支付成功加分（sc-order 经 Feign 调用）：按订单号幂等，重复回调只加一次。
     */
    boolean earn(Integer uId, String orderNo, int points, String remark);

    /**
     * 积分兑换优惠券：校验余额 → 扣分 → 调 sc-product 发券，发券失败补偿返还积分。
     */
    Map<String, Object> redeem(Integer uId, String optionKey);
}
