package com.example.scorder.controller;

import com.curry.model.annotation.OpLog;
import com.curry.model.auth.AuthConstant;
import com.example.scorder.dto.CartAddRequest;
import com.example.scorder.dto.CartBatchDeleteRequest;
import com.example.scorder.service.CartService;
import com.example.scorder.vo.CartItemVO;
import com.example.scorder.vo.CartMutationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

/**
 * 顾客购物车。身份一律取自网关注入的 X-User-Id，请求体里没有 uId，
 * 因此无法通过篡改参数读写他人购物车。
 * 挂在 /order/cart 下是为了复用网关既有的 Path=/order/** 路由与前端 devServer 代理，
 * 无需改动 sc-gateway 与 vue.config.js。
 * 结算不在这里 —— 前端把勾选行拼成 /order/placeOrderV2 的 items 提交，成功后再调 batchDelete 清车。
 */
@RestController
@RequestMapping(value = "/order/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    /**
     * 加入购物车。同商品重复加购按数量累加，超库存时截断并在返回体里带 capped=true。
     */
    @OpLog(module = "购物车", type = OpLog.OpType.ADD, description = "加入购物车")
    @PostMapping("/add")
    public ResponseDto<CartMutationVO> add(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestBody CartAddRequest request) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return cartService.addToCart(uId, request);
    }

    /**
     * 购物车列表。价格与库存每次回源 sc-product，返回的 effectivePrice 即前端下单要回传的 expectedPrice；
     * available / exceedStock 供前端禁用勾选，避免不可购买的行拖垮整批下单。
     */
    @GetMapping("/list")
    public ResponseDto<CartItemVO> list(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return cartService.listOwn(uId);
    }

    /**
     * 修改购物车中某商品的数量（覆盖，非累加）。服务端对最终数量有决定权，回传实际落库值。
     */
    @OpLog(module = "购物车", type = OpLog.OpType.UPDATE, description = "修改购物车数量")
    @PutMapping("/quantity")
    public ResponseDto<CartMutationVO> updateQuantity(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestBody CartAddRequest request) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return cartService.updateQuantityOwn(uId, request);
    }

    /**
     * 删除购物车中的某个商品。
     */
    @OpLog(module = "购物车", type = OpLog.OpType.DELETE, description = "删除购物车商品")
    @DeleteMapping("/{pId}")
    public ResponseDto<Integer> delete(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @PathVariable("pId") Integer pId) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return cartService.removeOwn(uId, pId);
    }

    /**
     * 批量删除（结算成功后清车、或手动删除所选）。
     * 用 POST 而非 DELETE：带 body 的 DELETE 在 axios/网关链路上支持不佳。
     * 以 pIds 为键、按 (u_id, p_id) 删除，天然幂等，重试无害。
     */
    @OpLog(module = "购物车", type = OpLog.OpType.DELETE, description = "批量删除购物车商品")
    @PostMapping("/batchDelete")
    public ResponseDto<Integer> batchDelete(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestBody CartBatchDeleteRequest request) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return cartService.removeBatchOwn(uId, request == null ? null : request.getPIds());
    }

    /**
     * 购物车条目数，供导航角标使用。返回商品种类数而非件数 —— 角标语义就是「几种商品」，
     * 且不必回读每行数量即恒定正确。
     */
    @GetMapping("/count")
    public ResponseDto<Integer> count(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return cartService.countOwn(uId);
    }
}
