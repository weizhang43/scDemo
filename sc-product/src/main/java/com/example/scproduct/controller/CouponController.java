package com.example.scproduct.controller;

import com.curry.model.annotation.OpLog;
import com.curry.model.auth.AuthConstant;
import com.example.scproduct.auth.AudienceResolver;
import com.example.scproduct.entity.CouponTemplate;
import com.example.scproduct.entity.UserCoupon;
import com.example.scproduct.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

import java.math.BigDecimal;

/**
 * 优惠券。挂在 /product/coupon 下复用现有网关路由与前端代理。
 * /inner/* 为 sc-order 服务间接口：lock 允许顾客身份透传但强校验本人，
 * use/restore 只接受内部调用（顾客身份一律拒绝，防止自还券重复使用）。
 */
@RestController
@RequestMapping("/product/coupon")
public class CouponController {

    @Autowired
    private CouponService couponService;

    /** 管理端：发布券模板（商家发自己的券，管理员可发平台券） */
    @OpLog(module = "营销管理", type = OpLog.OpType.ADD, description = "发布优惠券模板")
    @PostMapping("/template")
    public ResponseDto<CouponTemplate> createTemplate(@RequestBody CouponTemplate template) {
        return couponService.createTemplate(template, AudienceResolver.current());
    }

    /** 管理端：券模板分页 */
    @GetMapping("/template/pageQuery")
    public ResponseDto<CouponTemplate> pageQuery(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return couponService.pageQuery(pageNo, pageSize, AudienceResolver.current());
    }

    /** 管理端：停用券模板 */
    @OpLog(module = "营销管理", type = OpLog.OpType.UPDATE, description = "停用优惠券模板")
    @DeleteMapping("/template/{id}")
    public ResponseDto<CouponTemplate> disable(@PathVariable("id") Integer id) {
        return couponService.disable(id, AudienceResolver.current());
    }

    /** 顾客端：领券中心 */
    @GetMapping("/center")
    public ResponseDto<CouponTemplate> center(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        return couponService.center(uId);
    }

    /** 顾客端：领券 */
    @OpLog(module = "营销管理", type = OpLog.OpType.ADD, description = "领取优惠券")
    @PostMapping("/claim")
    public ResponseDto<UserCoupon> claim(
            @RequestParam("templateId") Integer templateId,
            @RequestHeader(AuthConstant.HEADER_X_USER_ID) Integer uId) {
        return couponService.claim(templateId, uId);
    }

    /** 顾客端：我的券 */
    @GetMapping("/mine")
    public ResponseDto<UserCoupon> mine(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestHeader(AuthConstant.HEADER_X_USER_ID) Integer uId) {
        return couponService.mine(uId, status);
    }

    /** 顾客端：结算页可用券（couponAmount 为按 orderAmount 算出的抵扣额） */
    @GetMapping("/usable")
    public ResponseDto<UserCoupon> usable(
            @RequestParam("orderAmount") BigDecimal orderAmount,
            @RequestHeader(AuthConstant.HEADER_X_USER_ID) Integer uId) {
        return couponService.usable(uId, orderAmount);
    }

    /** 内部：下单锁券。顾客身份透传时 uId 必须是本人 */
    @PostMapping("/inner/lock")
    public ResponseDto<UserCoupon> lock(
            @RequestParam("couponId") Integer couponId,
            @RequestParam("uId") Integer uId,
            @RequestParam("orderAmount") BigDecimal orderAmount,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer headerUId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer headerUType) {
        if (headerUId != null && headerUType != null
                && headerUType == AuthConstant.U_TYPE_CUSTOMER && !headerUId.equals(uId)) {
            return ResponseDto.error("无权使用他人的优惠券");
        }
        return couponService.lock(couponId, uId, orderAmount);
    }

    /** 内部：支付成功核销 */
    @PostMapping("/inner/use")
    public ResponseDto<UserCoupon> use(
            @RequestParam("couponId") Integer couponId,
            @RequestParam("oId") Integer oId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer headerUType) {
        if (headerUType != null && headerUType == AuthConstant.U_TYPE_CUSTOMER) {
            return ResponseDto.error("无权执行该操作");
        }
        return couponService.use(couponId, oId);
    }

    /** 内部：取消/退款返还 */
    @PostMapping("/inner/restore")
    public ResponseDto<UserCoupon> restore(
            @RequestParam("couponId") Integer couponId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer headerUType) {
        if (headerUType != null && headerUType == AuthConstant.U_TYPE_CUSTOMER) {
            return ResponseDto.error("无权执行该操作");
        }
        return couponService.restore(couponId);
    }
}
