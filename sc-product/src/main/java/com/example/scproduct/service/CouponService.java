package com.example.scproduct.service;

import com.example.scproduct.auth.AudienceScope;
import com.example.scproduct.entity.CouponTemplate;
import com.example.scproduct.entity.UserCoupon;
import response.ResponseDto;

import java.math.BigDecimal;

public interface CouponService {

    /** 发布券模板：管理员可发平台券(merchant_id=0)，商家只能以自己名义发 */
    ResponseDto<CouponTemplate> createTemplate(CouponTemplate template, AudienceScope scope);

    /** 管理端模板分页：商家看自己的 + 平台券 */
    ResponseDto<CouponTemplate> pageQuery(int pageNo, int pageSize, AudienceScope scope);

    /** 停用模板：status=0 且 Redis 库存置 0 */
    ResponseDto<CouponTemplate> disable(Integer id, AudienceScope scope);

    /** 领券中心：未过期的有效模板，带实时余量与当前用户已领标记 */
    ResponseDto<CouponTemplate> center(Integer uId);

    /** 领券：Redis Lua 防超发 + uk_tpl_user 兜底一人一张 */
    ResponseDto<UserCoupon> claim(Integer templateId, Integer uId);

    /** 我的券列表，status 非 null 时过滤 */
    ResponseDto<UserCoupon> mine(Integer uId, Integer status);

    /** 结算页可用券：未使用 + 有效期内 + 满足门槛，couponAmount 回填为按该订单额算出的抵扣额 */
    ResponseDto<UserCoupon> usable(Integer uId, BigDecimal orderAmount);

    /** 下单锁定：CAS 0→1，服务端按模板规则计算抵扣额并写快照，daoResult 返回带抵扣额的券 */
    ResponseDto<UserCoupon> lock(Integer couponId, Integer uId, BigDecimal orderAmount);

    /** 支付成功核销：CAS 1→2 绑定订单，重复核销同一订单幂等成功 */
    ResponseDto<UserCoupon> use(Integer couponId, Integer oId);

    /** 取消/退款返还：1|2→0 清空快照，天然幂等 */
    ResponseDto<UserCoupon> restore(Integer couponId);
}
