package com.example.scproduct.controller;

import com.curry.model.Product;
import com.curry.model.auth.AuthConstant;
import com.example.scproduct.service.UserInterestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

import java.util.List;

/**
 * 「猜你喜欢」推荐接口：收藏+足迹类目加权，游客/无行为数据时回退全站热销。
 */
@RestController
@RequestMapping("/product/recommend")
public class RecommendController {

    @Autowired
    private UserInterestService userInterestService;

    @GetMapping
    public ResponseDto<Product> recommend(@RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
                                          @RequestParam(value = "limit", defaultValue = "8") int limit) {
        // 游客或无行为数据统一走热销回退，保证推荐位任何时候都有内容
        return ResponseDto.success(userInterestService.recommend(uId, limit));
    }
}
