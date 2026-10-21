package com.example.scproduct.controller;

import com.curry.model.auth.AuthConstant;
import com.example.scproduct.service.UserInterestService;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 浏览足迹接口（登录顾客使用）。
 */
@RestController
@RequestMapping("/product/browse")
public class BrowseController {

    @Autowired
    private UserInterestService userInterestService;

    /** 记录足迹：商品详情页进入时调用，同商品重复浏览只刷新时间 */
    @PostMapping("/{pId}")
    public ResponseDto<Object> record(@RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
                                      @PathVariable("pId") Integer pId) {
        if (uId == null) {
            return ResponseDto.success(null);
        }
        userInterestService.recordBrowse(uId, pId);
        return ResponseDto.success(null);
    }

    /** 我的足迹（最近 N 条，默认 20） */
    @GetMapping("/list")
    public ResponseDto<Map<String, Object>> list(@RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
                                                 @RequestParam(value = "limit", defaultValue = "20") int limit) {
        if (uId == null) {
            return ResponseDto.error("请先登录");
        }
        return ResponseDto.success(userInterestService.browseList(uId, limit));
    }
}
