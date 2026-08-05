package com.example.scpay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scpay.entity.MockPayTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MockPayTxnMapper extends BaseMapper<MockPayTxn> {

    /**
     * 基于前置 status 的 CAS 更新。rows==0 表示状态已被其他请求变更，调用方据此做幂等返回。
     */
    @Update("UPDATE t_mock_pay_txn SET status=#{targetStatus}, update_time=NOW() " +
            "WHERE transaction_id=#{transactionId} AND status=#{expectStatus}")
    int casUpdateStatus(@Param("transactionId") String transactionId,
                        @Param("expectStatus") Integer expectStatus,
                        @Param("targetStatus") Integer targetStatus);

    /**
     * 回调结果记账：累加已回调次数并记录最近一次响应。
     */
    @Update("UPDATE t_mock_pay_txn SET notify_cnt=notify_cnt+1, last_notify_result=#{result}, update_time=NOW() " +
            "WHERE transaction_id=#{transactionId}")
    int recordNotifyResult(@Param("transactionId") String transactionId,
                           @Param("result") String result);
}
