package com.example.scorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scorder.entity.PayRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PayRecordMapper extends BaseMapper<PayRecord> {

    /**
     * 基于前置 status 的 CAS 更新。rows==0 表示状态已被其他请求变更，调用方据此走幂等/竞态分支。
     */
    @Update("UPDATE t_pay_record SET status=#{targetStatus}, version=version+1, update_time=NOW() " +
            "WHERE pay_no=#{payNo} AND status=#{expectStatus}")
    int casUpdateStatus(@Param("payNo") String payNo,
                        @Param("expectStatus") Integer expectStatus,
                        @Param("targetStatus") Integer targetStatus);

    /**
     * 支付成功专用 CAS：推进状态同时记支付/回调时间。
     */
    @Update("UPDATE t_pay_record SET status=#{targetStatus}, pay_time=NOW(), notify_time=NOW(), " +
            "version=version+1, update_time=NOW() " +
            "WHERE pay_no=#{payNo} AND status=#{expectStatus}")
    int casUpdateStatusPaid(@Param("payNo") String payNo,
                            @Param("expectStatus") Integer expectStatus,
                            @Param("targetStatus") Integer targetStatus);
}
