package com.example.scproduct.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.Product;
import com.example.scproduct.auth.AudienceScope;
import com.example.scproduct.vo.ProductQuery;
import com.example.scproduct.vo.ProductTypeCountVO;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

public interface ProductService extends IService<Product> {
    /**
     * 首页预警：查询三个月内即将过期的商品（到期日=生产日期+保质期天数）。
     */
    ResponseDto<Product> listExpiringSoon(AudienceScope scope);

    /**
     * 首页预警：查询库存低于阈值的商品（默认 100）。
     */
    ResponseDto<Product> listLowStock(int threshold, AudienceScope scope);

    /**
     * 顾客首页：好评榜，按点赞数倒序。点赞数联 t_product_like 现取，排序与展示同源。
     */
    ResponseDto<Product> listLikeRank(int limit, AudienceScope scope);

    /**
     * 顾客首页：上新货物，按 p_id 倒序（自增主键，最新录入即最新上架）。
     */
    ResponseDto<Product> listNewest(int limit, AudienceScope scope);

    /**
     * 按主键查询商品，不在可见范围内（如顾客访问已下架商品）时返回 null。
     */
    Product getVisibleById(Integer id, AudienceScope scope);

    /**
     * 按主键批量拉取「可售」商品（status=1）并回填有效价。
     * 已下架的商品不会出现在返回结果里 —— 调用方据此判断是否还能下单 / 是否该从榜单里剔除。
     */
    List<Product> listSellableByIds(List<Integer> ids);

    /**
     * 统计报表：按商品类型分组计数，商家只统计自己的商品与公共商品。
     */
    ResponseDto<ProductTypeCountVO> countByType(AudienceScope scope);

    /**
     * 商品列表分页查询：商品名称、商品描述、生产日期区间、产地、是否过期、上下架过滤。
     * 商品描述模糊检索下沉到 ES，ES 不可用时降级回 MySQL LIKE。
     * 结果始终带成交数与评价数。
     *
     * @param query 查询条件（status 1-在售 0-已下架 null 不限；categoryId 一级分类自动展开；
     *              sortBy sales-成交数 reviews-评价数 likes-点赞数，其余按 id 倒序）
     * @param scope 调用方可见范围
     */
    ResponseDto<Product> pageQuery(ProductQuery query, AudienceScope scope);

    /**
     * 新增商品：盖上创建者的 merchant_id，默认上架。
     */
    ResponseDto<Product> addOne(Product product, AudienceScope scope);

    /**
     * 按主键更新商品，先校验调用方有权管理该商品。
     */
    ResponseDto<Product> updateOne(Product product, AudienceScope scope);

    /**
     * 按主键删除商品，先校验调用方有权管理该商品。
     */
    ResponseDto<Product> removeOne(Integer id, AudienceScope scope);

    /**
     * 上架 / 下架商品（status 1-上架 0-下架），先校验调用方有权管理该商品。
     */
    ResponseDto<Product> setShelfStatus(Integer id, Integer status, AudienceScope scope);

    /**
     * 商品点赞：一个账号对一个商品只能点一次，重复点不加数。
     * uId 必须来自网关注入的 X-User-Id —— sc-product 的 AudienceScope.customer() 是共享单例，不带 uId。
     */
    ResponseDto<Product> like(Integer id, Integer uId);

    /**
     * 批量回读「我点过哪些商品」，供前端一次性渲染按钮态。
     */
    ResponseDto<Integer> listMyLikedPIds(Integer uId, List<Integer> pIds);

    /**
     * 将 Redis 中缓存的点赞数批量幂等回写到数据库。
     * @return 本次实际回写的商品数量
     */
    int flushLikeCount();

    /**
     * 批量扣减库存（下单）
     */
    ResponseDto<Product> deductStock(List<Product> products);

    /**
     * 批量校验库存充足后一次性扣减库存。
     * 入参 items: [{ pId, quantity }]
     * 任意一件库存不足 → 全部回滚（不扣），返回失败并带商品名。
     */
    ResponseDto<Product> checkAndDeductStock(List<Product> items);

    /**
     * 定时任务：扫描所有未过期商品，将生产日期+保质期 < 当前日期的标记为已过期，
     * 并把所有已过期商品统一下架（status=0）。
     * @return 形如 markedExpired=n, offShelf=n, costMs=n 的汇总，供 xxl-job 日志留痕
     */
    String markExpiredProducts();

    /**
     * 按查询条件导出商品列表为 Excel（EasyExcel）。
     *
     * @param query    查询条件（status/categoryId/sortBy/分页字段不参与，沿用旧的「不限上下架」语义）
     * @param scope    调用方可见范围
     * @param response HTTP 响应，Excel 直接写出到响应流
     */
    void export(ProductQuery query, AudienceScope scope, HttpServletResponse response) throws Exception;

    /**
     * 流式分页导出到本地文件，供异步线程调用。
     *
     * @param query               查询条件（pName/proDesc/productionDateStart/productionDateEnd/origin/isExpired）
     * @param outputPath          输出文件绝对路径
     * @param maxRows             单次导出最大行数（超过抛异常）
     * @param pageSize            每页查询行数
     * @param progressCallback    每写完一页后的进度回调（processed, total）-> void
     * @param cancelChecker       每页前调用，返回 true 表示任务已被取消
     * @return 实际写入行数
     */
    long exportToPath(ProductExportQuery query,
                      String outputPath,
                      long maxRows,
                      int pageSize,
                      ProgressCallback progressCallback,
                      CancelChecker cancelChecker) throws Exception;

    /**
     * 添加库存（补货 / 取消订单回库），先校验调用方有权管理这些商品。
     */
    ResponseDto<Product> addStock(List<Product> products, AudienceScope scope);

    /** 流式导出查询条件 */
    class ProductExportQuery {
        private final String pName;
        private final String proDesc;
        private final Date productionDateStart;
        private final Date productionDateEnd;
        private final String origin;
        private final Integer isExpired;
        /** 提交任务的调用方可见范围：异步导出线程内取不到请求头，只能随查询条件带过来 */
        private final AudienceScope scope;

        public ProductExportQuery(String pName, String proDesc, Date productionDateStart, Date productionDateEnd,
                                  String origin, Integer isExpired, AudienceScope scope) {
            this.pName = pName;
            this.proDesc = proDesc;
            this.productionDateStart = productionDateStart;
            this.productionDateEnd = productionDateEnd;
            this.origin = origin;
            this.isExpired = isExpired;
            this.scope = scope;
        }

        public String getpName() { return pName; }
        public String getProDesc() { return proDesc; }
        public Date getProductionDateStart() { return productionDateStart; }
        public Date getProductionDateEnd() { return productionDateEnd; }
        public String getOrigin() { return origin; }
        public Integer getIsExpired() { return isExpired; }
        public AudienceScope getScope() { return scope; }
    }

    @FunctionalInterface
    interface ProgressCallback {
        void onProgress(long processed, long total);
    }

    @FunctionalInterface
    interface CancelChecker {
        boolean isCanceled();
    }
}
