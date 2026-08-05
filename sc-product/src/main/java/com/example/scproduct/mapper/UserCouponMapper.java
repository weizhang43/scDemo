package com.example.scproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.scproduct.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /**
     * 我的券列表，联表带出模板名/规则/有效期。status 非 null 时过滤状态。
     */
    @Select("<script>" +
            "SELECT uc.*, t.name AS name, t.type AS type, t.threshold_amount AS thresholdAmount, " +
            "       t.off_amount AS offAmount, t.discount_rate AS discountRate, " +
            "       t.valid_start AS validStart, t.valid_end AS validEnd " +
            "FROM t_user_coupon uc JOIN t_coupon_template t ON t.id = uc.template_id " +
            "WHERE uc.u_id = #{uId} " +
            "<if test='status != null'> AND uc.status = #{status} </if>" +
            "ORDER BY uc.claim_time DESC, uc.id DESC" +
            "</script>")
    List<UserCoupon> selectMine(@Param("uId") Integer uId, @Param("status") Integer status);

    /**
     * 按用户券ID查详情（联模板），锁定/核销前的规则校验都取自这里。
     */
    @Select("SELECT uc.*, t.name AS name, t.type AS type, t.threshold_amount AS thresholdAmount, " +
            "       t.off_amount AS offAmount, t.discount_rate AS discountRate, " +
            "       t.valid_start AS validStart, t.valid_end AS validEnd " +
            "FROM t_user_coupon uc JOIN t_coupon_template t ON t.id = uc.template_id " +
            "WHERE uc.id = #{id}")
    UserCoupon selectDetailById(@Param("id") Integer id);

    /** CAS 0→1 下单锁定，同时写抵扣额快照。返回 0 表示券已被占用/已使用 */
    @Update("UPDATE t_user_coupon SET status = 1, coupon_amount = #{amount} " +
            "WHERE id = #{id} AND u_id = #{uId} AND status = 0")
    int casLock(@Param("id") Integer id, @Param("uId") Integer uId, @Param("amount") BigDecimal amount);

    /** CAS 1→2 支付成功核销，绑定订单留痕。幂等：已核销到同一订单时由服务层判定 */
    @Update("UPDATE t_user_coupon SET status = 2, o_id = #{oId}, use_time = NOW() " +
            "WHERE id = #{id} AND status = 1")
    int casUse(@Param("id") Integer id, @Param("oId") Integer oId);

    /** CAS 1|2→0 取消/退款返还，清空快照与留痕。天然幂等：已是 0 则影响 0 行 */
    @Update("UPDATE t_user_coupon SET status = 0, o_id = NULL, coupon_amount = NULL, use_time = NULL " +
            "WHERE id = #{id} AND status IN (1, 2)")
    int casRestore(@Param("id") Integer id);
}
