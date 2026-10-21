package com.example.scproduct.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.curry.model.Product;
import com.example.scproduct.entity.BrowseHistory;
import com.example.scproduct.entity.UserFavorite;
import com.example.scproduct.mapper.BrowseHistoryMapper;
import com.example.scproduct.mapper.ProductMapper;
import com.example.scproduct.mapper.UserFavoriteMapper;
import com.example.scproduct.service.PromotionService;
import com.example.scproduct.service.UserInterestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户兴趣服务实现。推荐为纯 SQL 类目加权（零 ML）：
 * 收藏类目 ×2 + 近30天足迹类目 ×1 → Top2 偏好类目在售商品按点赞数排序，
 * 不足 limit 时补全站热销（同样按点赞数），保证任何时候都有推荐结果。
 */
@Slf4j
@Service
public class UserInterestServiceImpl implements UserInterestService {

    /** 足迹加权时间窗（天） */
    private static final int HISTORY_WEIGHT_DAYS = 30;

    /** 推荐取偏好类目个数 */
    private static final int PREFER_CATEGORY_COUNT = 2;

    /** 每用户足迹保留条数上限 */
    private static final int HISTORY_KEEP_SIZE = 50;

    @Autowired
    private UserFavoriteMapper userFavoriteMapper;

    @Autowired
    private BrowseHistoryMapper browseHistoryMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private PromotionService promotionService;

    @Override
    public boolean favorite(Integer uId, Integer pId) {
        UserFavorite record = new UserFavorite();
        record.setUId(uId);
        record.setPId(pId);
        record.setCreateTime(new Date());
        try {
            return userFavoriteMapper.insert(record) > 0;
        } catch (DuplicateKeyException e) {
            return true;
        }
    }

    @Override
    public boolean unfavorite(Integer uId, Integer pId) {
        return userFavoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUId, uId)
                .eq(UserFavorite::getPId, pId)) > 0;
    }

    @Override
    public List<Map<String, Object>> favoriteList(Integer uId) {
        return userFavoriteMapper.selectFavoriteList(uId);
    }

    @Override
    public boolean isFavorited(Integer uId, Integer pId) {
        return userFavoriteMapper.selectCount(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUId, uId)
                .eq(UserFavorite::getPId, pId)) > 0;
    }

    @Override
    public void recordBrowse(Integer uId, Integer pId) {
        Product product = productMapper.selectById(pId);
        if (product == null) {
            return;
        }
        try {
            BrowseHistory history = new BrowseHistory();
            history.setUId(uId);
            history.setPId(pId);
            history.setCategoryId(product.getCategoryId());
            history.setCreateTime(new Date());
            browseHistoryMapper.insert(history);
        } catch (DuplicateKeyException e) {
            browseHistoryMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BrowseHistory>()
                    .eq(BrowseHistory::getUId, uId)
                    .eq(BrowseHistory::getPId, pId)
                    .set(BrowseHistory::getCategoryId, product.getCategoryId())
                    .set(BrowseHistory::getCreateTime, new Date()));
            return;
        }
        trimHistory(uId);
    }

    /**
     * 足迹只保留最近 HISTORY_KEEP_SIZE 条：先数总量，超限删掉最旧的溢出部分。
     */
    private void trimHistory(Integer uId) {
        try {
            Long count = browseHistoryMapper.selectCount(new LambdaQueryWrapper<BrowseHistory>()
                    .eq(BrowseHistory::getUId, uId));
            if (count == null || count <= HISTORY_KEEP_SIZE) {
                return;
            }
            List<BrowseHistory> oldest = browseHistoryMapper.selectList(new LambdaQueryWrapper<BrowseHistory>()
                    .eq(BrowseHistory::getUId, uId)
                    .orderByAsc(BrowseHistory::getId)
                    .last("LIMIT " + (count - HISTORY_KEEP_SIZE)));
            for (BrowseHistory row : oldest) {
                browseHistoryMapper.deleteById(row.getId());
            }
        } catch (Exception e) {
            log.warn("[Interest] 足迹清理失败 uId={}", uId, e);
        }
    }

    @Override
    public List<Map<String, Object>> browseList(Integer uId, int limit) {
        return browseHistoryMapper.selectHistoryList(uId, Math.min(Math.max(limit, 1), 100));
    }

    @Override
    public List<Product> recommend(Integer uId, int limit) {
        int size = Math.min(Math.max(limit, 1), 20);
        List<Integer> preferCategories = uId == null ? new ArrayList<>() : topPreferCategories(uId);
        Set<Integer> chosen = new HashSet<>();
        List<Product> result = new ArrayList<>();
        if (!preferCategories.isEmpty()) {
            List<Product> inCategory = productMapper.selectList(new LambdaQueryWrapper<Product>()
                    .select(Product::getPId, Product::getPName, Product::getPrice, Product::getImageUrl,
                            Product::getStock, Product::getCategoryId, Product::getLikeCount)
                    .in(Product::getCategoryId, preferCategories)
                    .eq(Product::getStatus, 1)
                    .eq(Product::getIsExpired, 0)
                    .orderByDesc(Product::getLikeCount)
                    .last("LIMIT " + size));
            for (Product p : inCategory) {
                if (chosen.add(p.getPId())) {
                    result.add(p);
                }
            }
        }
        if (result.size() < size) {
            List<Product> hot = productMapper.selectList(new LambdaQueryWrapper<Product>()
                    .select(Product::getPId, Product::getPName, Product::getPrice, Product::getImageUrl,
                            Product::getStock, Product::getCategoryId, Product::getLikeCount)
                    .eq(Product::getStatus, 1)
                    .eq(Product::getIsExpired, 0)
                    .orderByDesc(Product::getLikeCount)
                    .last("LIMIT " + (size * 2)));
            for (Product p : hot) {
                if (result.size() >= size) {
                    break;
                }
                if (chosen.add(p.getPId())) {
                    result.add(p);
                }
            }
        }
        promotionService.fillEffectivePrice(result);
        return result;
    }

    /**
     * 类目加权：收藏×2 与近30天足迹×1 合并求和，取权重最高的前 N 个类目。
     */
    private List<Integer> topPreferCategories(Integer uId) {
        Map<Integer, Integer> weights = new HashMap<>();
        mergeWeights(weights, userFavoriteMapper.selectFavoriteCategoryWeights(uId));
        mergeWeights(weights, browseHistoryMapper.selectRecentCategoryWeights(uId, HISTORY_WEIGHT_DAYS));
        return weights.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(PREFER_CATEGORY_COUNT)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }

    private static void mergeWeights(Map<Integer, Integer> weights, List<Map<String, Object>> rows) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            Object category = row.get("categoryId");
            Object weight = row.get("weight");
            if (category instanceof Number && weight instanceof Number) {
                Integer key = ((Number) category).intValue();
                weights.merge(key, ((Number) weight).intValue(), Integer::sum);
            }
        }
    }
}
