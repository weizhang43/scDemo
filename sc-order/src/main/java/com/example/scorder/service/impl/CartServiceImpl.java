package com.example.scorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Product;
import com.example.scorder.dto.CartAddRequest;
import com.example.scorder.entity.CartItem;
import com.example.scorder.mapper.CartItemMapper;
import com.example.scorder.service.CartService;
import com.example.scorder.service.OrderFeignService;
import com.example.scorder.vo.CartItemVO;
import com.example.scorder.vo.CartMutationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CartServiceImpl extends ServiceImpl<CartItemMapper, CartItem> implements CartService {

    /** 单个用户购物车最多容纳的商品种类数。同时给 listOwn 传给 Feign 的 IN (...) 封顶 */
    private static final int MAX_CART_ROWS = 50;

    /** 单个商品的加购数量上限 */
    private static final int MAX_QTY_PER_ITEM = 99;

    @Autowired
    private OrderFeignService orderFeignService;

    @Autowired
    private CartItemMapper cartItemMapper;

    /**
     * 加购。同一商品累加而非新增行（依赖 uk_u_id_p_id 的 upsert），
     * 累加结果超过实时库存时截断到库存并回传 capped=true —— 「只剩 1 件却想再加 2 件」
     * 直接拒掉整个操作对用户过于不友好。
     * 截断只是建议性的：之后库存还会变，最终由 listOwn 的 exceedStock 与下单时的
     * checkAndDeductStock 真实扣减兜底。
     */
    @Override
    public ResponseDto<CartMutationVO> addToCart(Integer uId, CartAddRequest req) {
        if (req == null || req.getPId() == null) {
            return ResponseDto.error("商品ID不能为空");
        }
        Integer pId = req.getPId();
        int qty = (req.getQuantity() == null || req.getQuantity() < 1) ? 1 : req.getQuantity();

        Product p = fetchSellable(pId);
        String reject = rejectReason(p, "加入购物车");
        if (reject != null) {
            return ResponseDto.error(reject);
        }

        Integer prevQty = cartItemMapper.selectQuantity(uId, pId);
        int maxQuantity = Math.min(p.getStock(), MAX_QTY_PER_ITEM);
        // 只在新增商品种类时判满：已在车里的商品继续加数量不占新坑位
        if (prevQty == null && cartItemMapper.countByUser(uId) >= MAX_CART_ROWS) {
            return ResponseDto.error("购物车已满（最多 " + MAX_CART_ROWS + " 种商品），请先结算或删除部分商品");
        }

        cartItemMapper.upsertAccumulate(uId, pId, Math.min(qty, maxQuantity), maxQuantity);
        Integer finalQty = cartItemMapper.selectQuantity(uId, pId);
        int expected = (prevQty == null ? 0 : prevQty) + qty;
        return ResponseDto.success(buildMutation(uId, pId, finalQty, finalQty != null && finalQty < expected));
    }

    /**
     * 覆盖式改数量。校验与加购同一套，SQL 换成 LEAST(目标值, 上限)。
     * 数量 0 不代表删除 —— 删除是独立接口，避免语义重载。
     */
    @Override
    public ResponseDto<CartMutationVO> updateQuantityOwn(Integer uId, CartAddRequest req) {
        if (req == null || req.getPId() == null) {
            return ResponseDto.error("商品ID不能为空");
        }
        if (req.getQuantity() == null || req.getQuantity() < 1) {
            return ResponseDto.error("数量必须大于 0");
        }
        Integer pId = req.getPId();
        int qty = Math.min(req.getQuantity(), MAX_QTY_PER_ITEM);

        Product p = fetchSellable(pId);
        String reject = rejectReason(p, "修改数量");
        if (reject != null) {
            return ResponseDto.error(reject);
        }

        int maxQuantity = Math.min(p.getStock(), MAX_QTY_PER_ITEM);
        int updated = cartItemMapper.updateQuantity(uId, pId, qty, maxQuantity);
        if (updated == 0) {
            return ResponseDto.error("购物车中没有该商品");
        }
        Integer finalQty = cartItemMapper.selectQuantity(uId, pId);
        return ResponseDto.success(buildMutation(uId, pId, finalQty, finalQty != null && finalQty < qty));
    }

    /**
     * 购物车列表。价格/库存/上架状态每次都向 sc-product 回源，不读快照 ——
     * effectivePrice 要拿去当下单的 expectedPrice，快照价必然撞「价格已更新」。
     * 因此下游异常时直接报错，不学 listSalesRank 的静默降级：销量榜少个价格只是缺装饰，
     * 而渲染出一份没有实时价的购物车，只会让用户点了结算再吃整批失败。
     */
    @Override
    public ResponseDto<CartItemVO> listOwn(Integer uId) {
        QueryWrapper<CartItem> wrapper = new QueryWrapper<>();
        wrapper.eq("u_id", uId).orderByDesc("update_time", "id");
        List<CartItem> items = cartItemMapper.selectList(wrapper);
        if (items == null || items.isEmpty()) {
            // 传空列表会生成 IN ()，在调 Feign 之前短路
            return ResponseDto.success(new ArrayList<CartItemVO>());
        }

        List<Integer> pIds = new ArrayList<>(new LinkedHashSet<>(
                items.stream().map(CartItem::getPId).collect(Collectors.toList())));
        Map<Integer, Product> sellable = new HashMap<>();
        try {
            ResponseDto<Product> prodResp = orderFeignService.listSellableByIds(pIds);
            if (prodResp == null || prodResp.getCode() == null || prodResp.getCode() != 200
                    || prodResp.getDataList() == null) {
                return ResponseDto.error("商品信息获取失败，请稍后重试");
            }
            for (Product p : prodResp.getDataList()) {
                if (p.getPId() != null) sellable.put(p.getPId(), p);
            }
        } catch (Exception e) {
            log.warn("[cart] 拉取商品实时信息失败, uId={}, err={}", uId, e.getMessage());
            return ResponseDto.error("商品信息获取失败，请稍后重试");
        }

        List<CartItemVO> rows = new ArrayList<>();
        for (CartItem item : items) {
            rows.add(toVO(item, sellable.get(item.getPId())));
        }
        return ResponseDto.success(rows);
    }

    @Override
    public ResponseDto<Integer> removeOwn(Integer uId, Integer pId) {
        if (pId == null) {
            return ResponseDto.error("商品ID不能为空");
        }
        // 归属条件写进 WHERE：越权删除只影响 0 行，不必先查后判
        QueryWrapper<CartItem> wrapper = new QueryWrapper<>();
        wrapper.eq("u_id", uId).eq("p_id", pId);
        cartItemMapper.delete(wrapper);
        return ResponseDto.success(cartItemMapper.countByUser(uId));
    }

    @Override
    public ResponseDto<Integer> removeBatchOwn(Integer uId, List<Integer> pIds) {
        if (pIds == null || pIds.isEmpty()) {
            return ResponseDto.error("请选择要删除的商品");
        }
        cartItemMapper.deleteBatchOwn(uId, pIds);
        return ResponseDto.success(cartItemMapper.countByUser(uId));
    }

    @Override
    public ResponseDto<Integer> countOwn(Integer uId) {
        return ResponseDto.success(cartItemMapper.countByUser(uId));
    }

    /**
     * 读实时商品。listSellableByIds 只返回 status=1 的商品且已回填 effectivePrice，
     * 查不到即已下架或不存在。
     */
    private Product fetchSellable(Integer pId) {
        try {
            ResponseDto<Product> resp = orderFeignService.listSellableByIds(Collections.singletonList(pId));
            if (resp != null && resp.getCode() != null && resp.getCode() == 200
                    && resp.getDataList() != null && !resp.getDataList().isEmpty()) {
                return resp.getDataList().get(0);
            }
        } catch (Exception e) {
            log.warn("[cart] 拉取商品失败, pId={}, err={}", pId, e.getMessage());
        }
        return null;
    }

    /**
     * 不可操作的原因，可操作时返回 null。措辞与 doPlaceOrder 的整批失败同族，
     * 让用户在加购入口就被拦住，而不是攒满一车再在结算时整批失败。
     */
    private String rejectReason(Product p, String action) {
        if (p == null) return "商品已下架或不存在，无法" + action;
        if (p.getIsExpired() != null && p.getIsExpired() == 1) return "商品已过保质期，无法" + action;
        if (p.getStock() == null || p.getStock() <= 0) return "商品已售罄，无法" + action;
        return null;
    }

    private CartMutationVO buildMutation(Integer uId, Integer pId, Integer quantity, boolean capped) {
        CartMutationVO vo = new CartMutationVO();
        vo.setPId(pId);
        vo.setQuantity(quantity);
        vo.setCapped(capped);
        vo.setCartCount(cartItemMapper.countByUser(uId));
        return vo;
    }

    /**
     * 三种不可售原因与 doPlaceOrder 的三种整批失败一一对应，前端据此禁用勾选，
     * 让会拖垮整批的行结构上进不了下单请求。
     */
    private CartItemVO toVO(CartItem item, Product p) {
        CartItemVO vo = new CartItemVO();
        vo.setId(item.getId());
        vo.setPId(item.getPId());
        vo.setQuantity(item.getQuantity());
        vo.setExceedStock(false);
        if (p == null) {
            vo.setStock(0);
            vo.setAvailable(false);
            vo.setUnavailableReason("商品已下架或不存在");
            return vo;
        }
        vo.setPName(p.getPName());
        vo.setImageUrl(p.getImageUrl());
        vo.setPrice(p.getPrice());
        vo.setDiscount(p.getDiscount());
        vo.setEffectivePrice(effectivePriceOf(p));
        int stock = p.getStock() == null ? 0 : p.getStock();
        vo.setStock(stock);
        if (p.getIsExpired() != null && p.getIsExpired() == 1) {
            vo.setAvailable(false);
            vo.setUnavailableReason("商品已过保质期");
        } else if (stock <= 0) {
            vo.setAvailable(false);
            vo.setUnavailableReason("商品已售罄");
        } else {
            vo.setAvailable(true);
            if (item.getQuantity() != null && item.getQuantity() > stock) {
                vo.setExceedStock(true);
                vo.setUnavailableReason("库存仅剩 " + stock + " 件，请调整数量");
            }
        }
        return vo;
    }

    /** 同 OrderServiceImpl#effectivePriceOf：折后价优先，无折扣回落整数原价 */
    private BigDecimal effectivePriceOf(Product p) {
        if (p == null) return BigDecimal.ZERO;
        if (p.getEffectivePrice() != null) return p.getEffectivePrice();
        return p.getPrice() == null ? BigDecimal.ZERO : BigDecimal.valueOf(p.getPrice());
    }
}
