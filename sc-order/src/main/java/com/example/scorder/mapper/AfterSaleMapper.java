package com.example.scorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.scorder.entity.AfterSale;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AfterSaleMapper extends BaseMapper<AfterSale> {

    /**
     * 售后分页查询：orderNo / uName 不落库，联 t_order / t_user 现取（同 ProductReviewMapper）。
     * uId 非空时只查该顾客自己的工单（商家/管理员传 null 查全量）。
     */
    @Select({
            "<script>",
            "SELECT a.id, a.o_id, a.u_id, a.type, a.reason, a.status, a.reject_reason,",
            "       a.refund_no, a.refund_amount, a.create_time, a.audit_time, a.refund_time,",
            "       o.order_no, u.u_name",
            "FROM t_after_sale a",
            "LEFT JOIN t_order o ON a.o_id = o.o_id",
            "LEFT JOIN t_user u ON a.u_id = u.u_id",
            "<where>",
            "  <if test='uId != null'>",
            "    AND a.u_id = #{uId}",
            "  </if>",
            "  <if test='status != null'>",
            "    AND a.status = #{status}",
            "  </if>",
            "</where>",
            "ORDER BY a.create_time DESC, a.id DESC",
            "</script>"
    })
    IPage<AfterSale> selectPageWithOrder(IPage<AfterSale> page,
                                         @Param("uId") Integer uId,
                                         @Param("status") Integer status);

    /**
     * 重新申请：被拒绝(3)/已取消(4)的工单复用同一行回到待审核，
     * 唯一键 uk_o_id 限制一单一工单，不再插新行。
     */
    @Update("UPDATE t_after_sale SET status=0, type=#{type}, reason=#{reason}, reject_reason=NULL, " +
            "refund_no=NULL, audit_time=NULL, refund_time=NULL, refund_amount=#{refundAmount}, " +
            "create_time=NOW(), update_time=NOW(), version=version+1 " +
            "WHERE o_id=#{oId} AND u_id=#{uId} AND status IN (3, 4)")
    int reapply(@Param("oId") Integer oId, @Param("uId") Integer uId,
                @Param("type") Integer type, @Param("reason") String reason,
                @Param("refundAmount") java.math.BigDecimal refundAmount);

    /** 顾客撤销申请：CAS 0→4 */
    @Update("UPDATE t_after_sale SET status=4, update_time=NOW(), version=version+1 " +
            "WHERE id=#{id} AND status=0 AND version=#{version}")
    int casCancel(@Param("id") Integer id, @Param("version") Integer version);

    /** 商家同意：CAS 0→1（退款中），记录退款依据的支付单号与审核时间 */
    @Update("UPDATE t_after_sale SET status=1, refund_no=#{refundNo}, audit_time=NOW(), " +
            "update_time=NOW(), version=version+1 " +
            "WHERE id=#{id} AND status=0 AND version=#{version}")
    int casApprove(@Param("id") Integer id, @Param("version") Integer version,
                   @Param("refundNo") String refundNo);

    /** 商家拒绝：CAS 0→3，记录拒绝原因 */
    @Update("UPDATE t_after_sale SET status=3, reject_reason=#{rejectReason}, audit_time=NOW(), " +
            "update_time=NOW(), version=version+1 " +
            "WHERE id=#{id} AND status=0 AND version=#{version}")
    int casReject(@Param("id") Integer id, @Param("version") Integer version,
                  @Param("rejectReason") String rejectReason);

    /**
     * 退款完成：CAS 1→2。由退款回执/重试任务驱动，不带 version ——
     * 多个驱动方并发时仅一方成功，其余幂等。
     */
    @Update("UPDATE t_after_sale SET status=2, refund_time=NOW(), update_time=NOW(), version=version+1 " +
            "WHERE id=#{id} AND status=1")
    int casFinishRefund(@Param("id") Integer id);
}
