package com.example.scproduct.controller;

import com.curry.model.auth.AuthConstant;
import com.example.scproduct.service.UserInterestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

import java.util.List;
import java.util.Map;

/**
 * 商品收藏接口（登录顾客使用，uId 取网关注入头）。
 */
@RestController
@RequestMapping("/product/favorite")
public class FavoriteController {

    @Autowired
    private UserInterestService userInterestService;

    /** 收藏商品，重复收藏幂等成功 */
    @PostMapping("/{pId}")
    public ResponseDto<Object> add(@RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
                                   @PathVariable("pId") Integer pId) {
        if (uId == null) {
            return ResponseDto.error("请先登录");
        }
        userInterestService.favorite(uId, pId);
        return ResponseDto.success(null);
    }

    /** 取消收藏 */
    @DeleteMapping("/{pId}")
    public ResponseDto<Object> remove(@RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
                                      @PathVariable("pId") Integer pId) {
        if (uId == null) {
            return ResponseDto.error("请先登录");
        }
        userInterestService.unfavorite(uId, pId);
        return ResponseDto.success(null);
    }

    /** 我的收藏列表 */
    @GetMapping("/list")
    public ResponseDto<Map<String, Object>> list(@RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        if (uId == null) {
            return ResponseDto.error("请先登录");
        }
        return ResponseDto.success(userInterestService.favoriteList(uId));
    }

    /** 是否已收藏（商品详情页心形状态） */
    @GetMapping("/status")
    public ResponseDto<Boolean> status(@RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
                                       @RequestParam("pId") Integer pId) {
        if (uId == null) {
            return ResponseDto.success(false);
        }
        return ResponseDto.success(userInterestService.isFavorited(uId, pId));
    }
}
