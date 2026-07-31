package com.example.scproduct.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Product;
import com.curry.model.ProductPromotion;
import com.example.scproduct.auth.AudienceScope;
import com.example.scproduct.mapper.ProductMapper;
import com.example.scproduct.mapper.ProductPromotionMapper;
import com.example.scproduct.service.PromotionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 折扣活动。
 * 直接依赖 ProductMapper 而非 ProductService：ProductServiceImpl 要反向依赖本类回填价格，
 * 走 Service 层会形成循环依赖。
 */
@Service
@Slf4j
public class PromotionServiceImpl extends ServiceImpl<ProductPromotionMapper, ProductPromotion>
        implements PromotionService {

    @Autowired
    private ProductPromotionMapper promotionMapper;

    @Autowired
    private ProductMapper productMapper;

    @Override
    public ResponseDto<ProductPromotion> create(ProductPromotion promotion, AudienceScope scope) {
        if (promotion == null || promotion.getPId() == null) {
            return ResponseDto.error("商品ID不能为空");
        }
        Integer discount = promotion.getDiscount();
        if (discount == null || discount < 1 || discount > 99) {
            return ResponseDto.error("折扣率必须在 1-99 之间（如 85 表示 8.5 折）");
        }
        Date start = promotion.getStartTime();
        Date end = promotion.getEndTime();
        if (start == null || end == null) {
            return ResponseDto.error("活动起止时间不能为空");
        }
        if (!end.after(start)) {
            return ResponseDto.error("结束时间必须晚于开始时间");
        }
        Product product = productMapper.selectById(promotion.getPId());
        if (product == null) {
            return ResponseDto.error("商品不存在");
        }
        if (!scope.canManage(product.getMerchantId())) {
            return ResponseDto.error("无权为该商品设置折扣");
        }
        // 时间窗重叠即拒绝，从根上消除「同时多个折扣生效、取哪个」的歧义
        long overlap = promotionMapper.selectCount(new LambdaQueryWrapper<ProductPromotion>()
                .eq(ProductPromotion::getPId, promotion.getPId())
                .lt(ProductPromotion::getStartTime, end)
                .gt(ProductPromotion::getEndTime, start));
        if (overlap > 0) {
            return ResponseDto.error("该商品在这个时间段已有折扣活动，请调整时间");
        }
        promotion.setId(null);
        promotion.setCreateTime(new Date());
        promotionMapper.insert(promotion);
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<ProductPromotion> cancel(Integer id, AudienceScope scope) {
        if (id == null) {
            return ResponseDto.error("活动ID不能为空");
        }
        ProductPromotion promotion = promotionMapper.selectById(id);
        if (promotion == null) {
            return ResponseDto.error("折扣活动不存在");
        }
        Product product = productMapper.selectById(promotion.getPId());
        if (product != null && !scope.canManage(product.getMerchantId())) {
            return ResponseDto.error("无权取消该折扣活动");
        }
        promotionMapper.deleteById(id);
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<ProductPromotion> pageQuery(Integer pId, int pageNo, int pageSize, AudienceScope scope) {
        Page<ProductPromotion> page = new Page<>(pageNo, pageSize);
        IPage<ProductPromotion> result = promotionMapper.selectPromotionPage(page, pId, scope.getMerchantId());
        for (ProductPromotion p : result.getRecords()) {
            if (p.getPrice() != null && p.getDiscount() != null) {
                p.setEffectivePrice(discountedPrice(p.getPrice(), p.getDiscount()));
            }
        }
        return ResponseDto.success(result);
    }

    @Override
    public void fillEffectivePrice(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        List<Integer> pIds = products.stream()
                .map(Product::getPId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (pIds.isEmpty()) {
            return;
        }
        Map<Integer, ProductPromotion> active = findActive(pIds);
        for (Product p : products) {
            if (p.getPrice() == null) {
                continue;
            }
            ProductPromotion promotion = active.get(p.getPId());
            if (promotion == null) {
                p.setEffectivePrice(BigDecimal.valueOf(p.getPrice()));
            } else {
                p.setDiscount(promotion.getDiscount());
                p.setEffectivePrice(discountedPrice(p.getPrice(), promotion.getDiscount()));
            }
        }
    }

    /**
     * 生效判定唯一实现：now 落在 [start_time, end_time] 内。
     * 创建时已拒绝重叠，此处的取舍只为并发/存量数据给出确定答案：
     * 折扣力度大的优先（discount 值小），完全相同则取小 id。
     */
    private Map<Integer, ProductPromotion> findActive(Collection<Integer> pIds) {
        Date now = new Date();
        List<ProductPromotion> rows = promotionMapper.selectList(new LambdaQueryWrapper<ProductPromotion>()
                .in(ProductPromotion::getPId, pIds)
                .le(ProductPromotion::getStartTime, now)
                .ge(ProductPromotion::getEndTime, now));
        Map<Integer, ProductPromotion> active = new HashMap<>();
        for (ProductPromotion row : rows) {
            if (row.getDiscount() == null) {
                continue;
            }
            ProductPromotion current = active.get(row.getPId());
            if (current == null
                    || row.getDiscount() < current.getDiscount()
                    || (row.getDiscount().equals(current.getDiscount()) && row.getId() < current.getId())) {
                active.put(row.getPId(), row);
            }
        }
        return active;
    }

    /**
     * 折后价。price 是整数元，33 元打 8.5 折 = 28.05，必须用 BigDecimal 保留两位小数。
     */
    private static BigDecimal discountedPrice(Integer price, Integer discount) {
        return BigDecimal.valueOf(price)
                .multiply(BigDecimal.valueOf(discount))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
