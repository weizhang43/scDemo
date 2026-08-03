package com.example.scorder.controller;

import com.curry.model.annotation.OpLog;
import com.curry.model.auth.AuthConstant;
import com.example.scorder.dto.ReviewSubmitRequest;
import com.example.scorder.entity.ProductReview;
import com.example.scorder.service.ReviewService;
import com.example.scorder.vo.ProductReviewSummaryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

/**
 * 商品评价。身份一律取自网关注入的 X-User-Id，请求体里没有 uId。
 * 挂在 /order/review 下是为了复用网关既有的 Path=/order/** 路由与前端 devServer 代理，
 * 无需改动 sc-gateway 与 vue.config.js（同 CartController）。
 */
@RestController
@RequestMapping(value = "/order/review")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    /**
     * 发表评价：一到五星 + 可选文字。同一订单同一商品只能评一次。
     */
    @OpLog(module = "商品评价", type = OpLog.OpType.ADD, description = "发表商品评价")
    @PostMapping
    public ResponseDto<ProductReview> submit(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestBody ReviewSubmitRequest request) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return reviewService.submitOwn(uId, request);
    }

    /**
     * 某商品的评价列表，顾客端与商家端商品详情页共用。
     */
    @GetMapping("/product/{pId}")
    public ResponseDto<ProductReviewSummaryVO> listByProduct(
            @PathVariable("pId") Integer pId,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "5") int pageSize) {
        return reviewService.listByProduct(pId, pageNo, pageSize);
    }

    /**
     * 我的历史评价。
     */
    @GetMapping("/mine")
    public ResponseDto<ProductReview> mine(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return reviewService.listMine(uId, pageNo, pageSize);
    }

    /**
     * 某订单已评过的商品 pId 列表，供前端渲染按钮态。
     */
    @GetMapping("/order/{oId}")
    public ResponseDto<Integer> reviewedPIds(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @PathVariable("oId") Integer oId) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return reviewService.listReviewedPIdsOwn(uId, oId);
    }
}
