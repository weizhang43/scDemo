package com.example.scproduct.service;

import com.curry.model.Product;

import java.util.List;
import java.util.Map;

/**
 * 用户兴趣服务：收藏、浏览足迹与「猜你喜欢」推荐。
 */
public interface UserInterestService {

    /**
     * 收藏商品（幂等：唯一键冲突视为成功）。
     */
    boolean favorite(Integer uId, Integer pId);

    /**
     * 取消收藏。
     */
    boolean unfavorite(Integer uId, Integer pId);

    /**
     * 我的收藏列表（联商品信息）。
     */
    List<Map<String, Object>> favoriteList(Integer uId);

    /**
     * 是否已收藏该商品。
     */
    boolean isFavorited(Integer uId, Integer pId);

    /**
     * 记录浏览足迹（同商品刷新时间），并清理超出上限的旧足迹。
     */
    void recordBrowse(Integer uId, Integer pId);

    /**
     * 我的浏览足迹（最近 N 条，联商品信息）。
     */
    List<Map<String, Object>> browseList(Integer uId, int limit);

    /**
     * 猜你喜欢：基于收藏(×2)+近30天足迹(×1)的类目加权，
     * 取偏好类目在售商品按点赞数排序，不足补全站热销。
     */
    List<Product> recommend(Integer uId, int limit);
}
