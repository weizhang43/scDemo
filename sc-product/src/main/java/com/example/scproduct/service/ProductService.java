package com.example.scproduct.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.Product;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

public interface ProductService extends IService<Product> {
    /**
     * 演示链路：插入一条固定名称的垃圾袋商品（演示链路，仅用于联调）。
     */
    ResponseDto<Product> addProduct();

    /**
     * 分页查找商品
     * @param key
     * @param price
     * @param pageNo
     * @param pageSize
     * @return
     */
    ResponseDto<Product> queryProduct(String key,int price,int pageNo,int pageSize);

    /**
     * 商品列表分页查询：商品名称、商品描述、生产日期区间、产地、是否过期过滤，按 id 倒序。
     * 商品描述模糊检索下沉到 ES，ES 不可用时降级回 MySQL LIKE。
     */
    ResponseDto<Product> pageQuery(String pName,
                                   String proDesc,
                                   Date productionDateStart,
                                   Date productionDateEnd,
                                   String origin,
                                   Integer isExpired,
                                   int pageNo,
                                   int pageSize);

    /**
     * 新增商品
     */
    ResponseDto<Product> addOne(Product product);

    /**
     * 商品点赞：点赞数量 +1，返回最新商品
     */
    ResponseDto<Product> like(Integer id);

    /**
     * 将 Redis 中缓存的点赞数批量幂等回写到数据库。
     * @return 本次实际回写的商品数量
     */
    int flushLikeCount();

    /**
     * 秒杀预扣库存（Redis 原子操作，一人一单）。
     * 库存未加载时从 DB 懒加载并校验可秒杀（未过期、库存>=1）。
     * @return code=200 预扣成功；否则 msg 为失败原因（已售罄/已参与/不可秒杀）
     */
    ResponseDto<Product> seckillPreDeduct(Integer pId, Integer uId);

    /**
     * 秒杀补偿：回滚 Redis 预扣库存并移除用户已购标记（落库失败时调用）。
     */
    ResponseDto<Product> rollbackSeckillStock(Integer pId, Integer uId);

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
     * 扫描所有未过期商品，将生产日期+保质期 < 当前日期的标记为已过期。
     * @return 本次被标记为已过期的商品数量
     */
    int markExpiredProducts();

    /**
     * 按查询条件导出商品列表为 Excel（EasyExcel）。
     */
    void export(String pName,
                String proDesc,
                Date productionDateStart,
                Date productionDateEnd,
                String origin,
                Integer isExpired,
                HttpServletResponse response) throws Exception;

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
     * 添加库存
     * @param products
     * @return
     */
    ResponseDto<Product> addStock(List<Product> products);

    /** 流式导出查询条件 */
    class ProductExportQuery {
        private final String pName;
        private final String proDesc;
        private final Date productionDateStart;
        private final Date productionDateEnd;
        private final String origin;
        private final Integer isExpired;

        public ProductExportQuery(String pName, String proDesc, Date productionDateStart, Date productionDateEnd,
                                  String origin, Integer isExpired) {
            this.pName = pName;
            this.proDesc = proDesc;
            this.productionDateStart = productionDateStart;
            this.productionDateEnd = productionDateEnd;
            this.origin = origin;
            this.isExpired = isExpired;
        }

        public String getpName() { return pName; }
        public String getProDesc() { return proDesc; }
        public Date getProductionDateStart() { return productionDateStart; }
        public Date getProductionDateEnd() { return productionDateEnd; }
        public String getOrigin() { return origin; }
        public Integer getIsExpired() { return isExpired; }
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
