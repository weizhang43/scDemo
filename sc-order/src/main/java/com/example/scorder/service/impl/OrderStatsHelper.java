package com.example.scorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.curry.model.Product;
import com.example.scorder.auth.OrderScope;
import com.example.scorder.entity.AfterSale;
import com.example.scorder.mapper.AfterSaleMapper;
import com.example.scorder.mapper.OrderItemMapper;
import com.example.scorder.mapper.OrderMapper;
import com.example.scorder.service.OrderFeignService;
import com.example.scorder.vo.DailySalesVO;
import com.example.scorder.vo.DashboardOverviewVO;
import com.example.scorder.vo.MonthlySalesVO;
import com.example.scorder.vo.ProductSalesRankVO;
import com.example.scorder.vo.TypeSalesVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import response.ResponseDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单统计辅助：销量榜、类型/月度/逐日销量、工作台概览与状态分组计数。
 * 从 OrderServiceImpl 拆出以控制单文件规模，仅被其委托调用。
 */
@Component
public class OrderStatsHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderStatsHelper.class);

    /** 统计销量榜近三个自然月的月份数 */
    private static final int MONTHLY_SALES_MONTHS = 3;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private AfterSaleMapper afterSaleMapper;

    @Autowired
    private OrderFeignService orderFeignService;

    /**
     * 商品销量榜：按销量聚合后回填商品价格/图片，商家身份只统计自己的商品与公共商品。
     */
    public ResponseDto<ProductSalesRankVO> listSalesRank(int limit, Integer merchantId) {
        List<ProductSalesRankVO> rank = new ArrayList<>();
        for (Map<String, Object> row : orderItemMapper.selectSalesRank(limit, merchantId)) {
            rank.add(toRankVO(row));
        }
        if (rank.isEmpty()) {
            // 传空列表会生成 IN ()，直接短路
            return ResponseDto.success(rank);
        }
        return ResponseDto.success(enrichSalesRank(rank));
    }

    /**
     * 销量榜聚合行转 VO。
     */
    private ProductSalesRankVO toRankVO(Map<String, Object> row) {
        ProductSalesRankVO vo = new ProductSalesRankVO();
        vo.setPId(row.get("pId") == null ? null : ((Number) row.get("pId")).intValue());
        vo.setPName((String) row.get("pName"));
        vo.setSalesCount(row.get("salesCount") == null ? 0L : ((Number) row.get("salesCount")).longValue());
        return vo;
    }

    /**
     * 补当前价格与图片；补不到就只展示名称+销量，不因下游异常让整个榜单失败。
     */
    private List<ProductSalesRankVO> enrichSalesRank(List<ProductSalesRankVO> rank) {
        try {
            List<Integer> pIds = rank.stream().map(ProductSalesRankVO::getPId).collect(Collectors.toList());
            ResponseDto<Product> prodResp = orderFeignService.listSellableByIds(pIds);
            if (!OrderSupport.isSuccess(prodResp) || prodResp.getDataList() == null) {
                return rank;
            }
            return filterSellableRank(rank, toProductMap(prodResp.getDataList()));
        } catch (Exception e) {
            LOGGER.warn("[salesRank] 拉取商品价格/图片失败", e);
            return rank;
        }
    }

    /**
     * listSellableByIds 只返回上架商品，查不到即已下架 —— 顾客首页要看不到。
     */
    private List<ProductSalesRankVO> filterSellableRank(List<ProductSalesRankVO> rank,
                                                        Map<Integer, Product> prodMap) {
        List<ProductSalesRankVO> sellableRank = new ArrayList<>();
        for (ProductSalesRankVO vo : rank) {
            Product p = prodMap.get(vo.getPId());
            if (p != null) {
                fillRankProductInfo(vo, p);
                sellableRank.add(vo);
            }
        }
        return sellableRank;
    }

    /**
     * 回填榜单行的价格/图片/折扣/有效价与最新名称。
     */
    private void fillRankProductInfo(ProductSalesRankVO vo, Product p) {
        vo.setPrice(p.getPrice());
        vo.setImageUrl(p.getImageUrl());
        vo.setDiscount(p.getDiscount());
        vo.setEffectivePrice(OrderSupport.effectivePriceOf(p));
        if (p.getPName() != null) {
            vo.setPName(p.getPName());
        }
    }

    /**
     * 商品列表按商品ID建索引。
     */
    private Map<Integer, Product> toProductMap(List<Product> products) {
        Map<Integer, Product> prodMap = new HashMap<>();
        for (Product p : products) {
            if (p.getPId() != null) {
                prodMap.put(p.getPId(), p);
            }
        }
        return prodMap;
    }

    /**
     * 销量按商品类型分组统计。
     */
    public ResponseDto<TypeSalesVO> listTypeSales(Integer merchantId) {
        return ResponseDto.success(orderItemMapper.selectTypeSales(merchantId));
    }

    /**
     * 折线图断月会误导趋势，SQL 只返回有销量的月份，这里按近三个自然月骨架补 0。
     */
    public ResponseDto<MonthlySalesVO> listMonthlySales(Integer merchantId) {
        Map<String, Long> salesByMonth = new HashMap<>();
        for (MonthlySalesVO vo : orderItemMapper.selectMonthlySales(merchantId)) {
            salesByMonth.put(vo.getMonth(), vo.getSalesCount() == null ? 0L : vo.getSalesCount());
        }
        List<MonthlySalesVO> result = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = MONTHLY_SALES_MONTHS - 1; i >= 0; i--) {
            String month = now.minusMonths(i).toString();
            result.add(new MonthlySalesVO(month, salesByMonth.getOrDefault(month, 0L)));
        }
        return ResponseDto.success(result);
    }

    /**
     * 首页工作台概览：今日成交额/单量、待发货、待付款、待处理售后。
     */
    public ResponseDto<DashboardOverviewVO> dashboardOverview(Integer merchantId) {
        DashboardOverviewVO vo = new DashboardOverviewVO();
        Map<String, Object> today = merchantId == null
                ? orderMapper.selectTodayOverviewAll()
                : orderItemMapper.selectTodayOverviewByMerchant(merchantId);
        vo.setTodayGmv(today == null || today.get("todayGmv") == null
                ? BigDecimal.ZERO : new BigDecimal(today.get("todayGmv").toString()));
        vo.setTodayOrderCount(today == null || today.get("todayOrderCount") == null
                ? 0L : ((Number) today.get("todayOrderCount")).longValue());

        // 待发货/待付款：复用状态分组统计（商家/管理员 scope 均为全量，与订单列表口径一致）
        Map<String, Long> statusCount = countByStatus(null, null, null, null, OrderScope.unrestricted());
        vo.setPendingShipCount(
                statusCount.getOrDefault(String.valueOf(OrderServiceImpl.PLACED_ORDER_STATUS), 0L));
        vo.setUnpaidCount(
                statusCount.getOrDefault(String.valueOf(OrderServiceImpl.UN_COMMIT_ORDER_STATUS), 0L));

        vo.setPendingAfterSaleCount(afterSaleMapper.selectCount(
                new LambdaQueryWrapper<AfterSale>()
                        .eq(AfterSale::getStatus, 0)).longValue());
        return ResponseDto.success(vo);
    }

    /**
     * 近 N 天逐日成交趋势：SQL 只返回有成交的日期，这里按天数骨架补 0。
     */
    public ResponseDto<DailySalesVO> listDailySales(Integer merchantId, int days) {
        List<Map<String, Object>> rows = merchantId == null
                ? orderMapper.selectDailySalesAll(days)
                : orderItemMapper.selectDailySalesByMerchant(merchantId, days);
        Map<String, DailySalesVO> byDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String date = String.valueOf(row.get("date"));
            BigDecimal gmv = row.get("gmv") == null ? BigDecimal.ZERO : new BigDecimal(row.get("gmv").toString());
            Long cnt = row.get("orderCount") == null ? 0L : ((Number) row.get("orderCount")).longValue();
            byDate.put(date, new DailySalesVO(date, gmv, cnt));
        }
        List<DailySalesVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            result.add(byDate.getOrDefault(date, new DailySalesVO(date, BigDecimal.ZERO, 0L)));
        }
        return ResponseDto.success(result);
    }

    /**
     * 统计各订单状态数量：无论是否有该状态订单，均返回 -1/0/1/2/3 五个 key，缺省为 0。
     */
    public Map<String, Long> countByStatus(String key, String orderNo, Date createTimeStart,
                                           Date createTimeEnd, OrderScope scope) {
        Map<String, Long> result = new HashMap<>();
        result.put(String.valueOf(OrderServiceImpl.UN_COMMIT_ORDER_STATUS), 0L);
        result.put(String.valueOf(OrderServiceImpl.PLACED_ORDER_STATUS), 0L);
        result.put(String.valueOf(OrderServiceImpl.SHIPPED_ORDER_STATUS), 0L);
        result.put(String.valueOf(OrderServiceImpl.COMPLETE_ORDER_STATUS), 0L);
        result.put(String.valueOf(OrderServiceImpl.CANCEL_ORDER_STATUS), 0L);
        List<Map<String, Object>> rows = orderMapper.countGroupByStatus(
                key, orderNo, createTimeStart, createTimeEnd, scope.getOwnerUId());
        for (Map<String, Object> row : rows) {
            Object status = row.get("orderStatus");
            Object cnt = row.get("cnt");
            if (status != null) {
                result.put(String.valueOf(status), cnt == null ? 0L : ((Number) cnt).longValue());
            }
        }
        return result;
    }
}
