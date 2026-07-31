package com.example.scproduct.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.curry.model.Product;
import com.curry.model.annotation.OpLog;
import com.example.scproduct.auth.AudienceResolver;
import com.example.scproduct.auth.AudienceScope;
import com.example.scproduct.service.ExportTaskService;
import com.example.scproduct.service.ProductService;
import com.example.scproduct.vo.ExportTaskVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;

@RestController
@RequestMapping(value = "/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ExportTaskService exportTaskService;

    private static final List<String> PRODUCT_LIST =
            Arrays.asList("垃圾袋", "手机","电脑","衣服","雨伞",
                    "零食","汽车","饮料","电风扇","手机壳");

    /** 首页预警：三个月内即将过期的商品 */
    @GetMapping("/warning/expiring")
    public ResponseDto<Product> expiringSoon() {
        return productService.listExpiringSoon(AudienceResolver.current());
    }

    /** 首页预警：库存不足的商品（阈值默认 100） */
    @GetMapping("/warning/lowStock")
    public ResponseDto<Product> lowStock(@RequestParam(value = "threshold", defaultValue = "100") Integer threshold) {
        return productService.listLowStock(threshold, AudienceResolver.current());
    }

    /** 顾客首页：好评榜，按点赞数倒序 */
    @GetMapping("/rank/likes")
    public ResponseDto<Product> likeRank(@RequestParam(value = "limit", defaultValue = "10") int limit) {
        return productService.listLikeRank(Math.min(Math.max(limit, 1), 50), AudienceResolver.current());
    }

    /** 顾客首页：上新货物 */
    @GetMapping("/rank/newest")
    public ResponseDto<Product> newest(@RequestParam(value = "limit", defaultValue = "8") int limit) {
        return productService.listNewest(Math.min(Math.max(limit, 1), 50), AudienceResolver.current());
    }



    /**
     * 批量生成 100 条随机商品（演示链路），通过 saveBatch 分批入库。
     */
    @PostMapping("/createProduct")
    public ResponseDto<Product> createProduct() {
        List<Product> productList = new java.util.ArrayList<>();
        for(int i=0; i<100; i++){
            Product product = new Product();
            Random rand = new Random();
            int randomNumber = rand.nextInt(10);
            product.setPName(PRODUCT_LIST.get(randomNumber));
            product.setPrice((randomNumber+i)*100);
            productList.add(product);
        }
        productService.saveBatch(productList,5);
        return ResponseDto.success(null);
    }

    /**
     * 按主键查询商品。顾客访问已下架商品、商家访问他人商品均视为不存在。
     */
    @GetMapping("/{id}")
    public Product get(@PathVariable("id") Integer id) {
        return productService.getVisibleById(id, AudienceResolver.current());
    }

    /**
     * 根据 ID 列表批量查「可售」商品：已下架的不会返回。
     * 顾客侧榜单与下单取价都走这里，避免下架商品漏出。
     */
    @GetMapping("/listSellableByIds")
    public ResponseDto<Product> listSellableByIds(@RequestParam("ids") List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseDto.success(null);
        }
        return ResponseDto.success(productService.listSellableByIds(ids));
    }

    /**
     * 按主键更新商品信息（含补货）。归属校验不通过时拒绝。
     */
    @OpLog(module = "商品管理", type = OpLog.OpType.UPDATE, description = "修改商品")
    @PutMapping
    public ResponseDto<Product> update(@RequestBody Product product) {
        return productService.updateOne(product, AudienceResolver.current());
    }

    /**
     * 上架 / 下架商品。下架后顾客端一律查不到。
     */
    @OpLog(module = "商品管理", type = OpLog.OpType.UPDATE, description = "商品上下架")
    @PutMapping("/shelf/{id}")
    public ResponseDto<Product> shelf(@PathVariable("id") Integer id,
                                      @RequestParam("status") Integer status) {
        return productService.setShelfStatus(id, status, AudienceResolver.current());
    }

    /**
     * 按主键删除商品。归属校验不通过时拒绝。
     */
    @OpLog(module = "商品管理", type = OpLog.OpType.DELETE, description = "删除商品")
    @DeleteMapping("/{id}")
    public ResponseDto<Product> delete(@PathVariable("id") Integer id) {
        return productService.removeOne(id, AudienceResolver.current());
    }

    /**
     * 商品列表分页查询：商品名称、商品描述、生产日期区间、产地、是否过期过滤，按 id 倒序。
     * 商品描述模糊检索下沉到 ES，ES 不可用时降级回 MySQL LIKE。
     */
    @GetMapping("/pageQuery")
    @SentinelResource(value = "product-pageQuery", blockHandler = "pageQueryBlockHandler")
    public ResponseDto<Product> pageQuery(@RequestParam(value = "pName", required = false) String pName,
                                          @RequestParam(value = "proDesc", required = false) String proDesc,
                                          @RequestParam(value = "productionDateStart", required = false)
                                          @DateTimeFormat(pattern = "yyyy-MM-dd") Date productionDateStart,
                                          @RequestParam(value = "productionDateEnd", required = false)
                                          @DateTimeFormat(pattern = "yyyy-MM-dd") Date productionDateEnd,
                                          @RequestParam(value = "origin", required = false) String origin,
                                          @RequestParam(value = "isExpired", required = false) Integer isExpired,
                                          @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                          @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return productService.pageQuery(pName, proDesc, productionDateStart, productionDateEnd, origin, isExpired,
                pageNo, pageSize, AudienceResolver.current());
    }

    /**
     * pageQuery 限流兜底：参数列表须与原方法一致，末尾追加 BlockException
     */
    public ResponseDto<Product> pageQueryBlockHandler(String pName, String proDesc, Date productionDateStart, Date productionDateEnd,
                                                      String origin, Integer isExpired, int pageNo, int pageSize,
                                                      BlockException ex) {
        return ResponseDto.error("请求过于频繁，请稍后再试");
    }

    /**
     * 新增商品
     */
    @OpLog(module = "商品管理", type = OpLog.OpType.ADD, description = "新增商品")
    @PostMapping("/add")
    public ResponseDto<Product> add(@RequestBody Product product) {
        return productService.addOne(product, AudienceResolver.current());
    }

    /**
     * 商品点赞：点赞数量 +1
     */
    @PostMapping("/like/{id}")
    public ResponseDto<Product> like(@PathVariable("id") Integer id) {
        return productService.like(id);
    }

    /**
     * 批量下单：扣减库存
     */
    @PostMapping("/deductStock")
    public ResponseDto<Product> deductStock(@RequestBody List<Product> products) {
        return productService.deductStock(products);
    }

    /**
     * 添加库存
     */
    @PostMapping("/addStock")
    public ResponseDto<Product> addStock(@RequestBody List<Product> products) {
        try{
            return productService.addStock(products, AudienceResolver.current());
        }catch (Exception e){
            return ResponseDto.error("修改库存失败");
        }

    }

    /**
     * 批量下单：校验+原子扣减库存（库存不足全部回滚）
     */
    @PostMapping("/checkAndDeductStock")
    public ResponseDto<Product> checkAndDeductStock(@RequestBody List<Product> items) {
        return productService.checkAndDeductStock(items);
    }

    /**
     * 按查询条件导出商品列表（EasyExcel）
     */
    @GetMapping("/export")
    public void export(@RequestParam(value = "pName", required = false) String pName,
                       @RequestParam(value = "proDesc", required = false) String proDesc,
                       @RequestParam(value = "productionDateStart", required = false)
                       @DateTimeFormat(pattern = "yyyy-MM-dd") Date productionDateStart,
                       @RequestParam(value = "productionDateEnd", required = false)
                       @DateTimeFormat(pattern = "yyyy-MM-dd") Date productionDateEnd,
                       @RequestParam(value = "origin", required = false) String origin,
                       @RequestParam(value = "isExpired", required = false) Integer isExpired,
                       HttpServletResponse response) throws Exception {
        productService.export(pName, proDesc, productionDateStart, productionDateEnd, origin, isExpired,
                AudienceResolver.current(), response);
    }

    /**
     * 异步导出：提交任务，立即返回 taskId。
     */
    @GetMapping("/export/async")
    public ResponseDto<Product> exportAsync(@RequestParam(value = "pName", required = false) String pName,
                                             @RequestParam(value = "proDesc", required = false) String proDesc,
                                             @RequestParam(value = "productionDateStart", required = false)
                                             @DateTimeFormat(pattern = "yyyy-MM-dd") Date productionDateStart,
                                             @RequestParam(value = "productionDateEnd", required = false)
                                             @DateTimeFormat(pattern = "yyyy-MM-dd") Date productionDateEnd,
                                             @RequestParam(value = "origin", required = false) String origin,
                                             @RequestParam(value = "isExpired", required = false) Integer isExpired) {
        // 异步导出跑在线程池里取不到请求头，scope 必须在此解析后随查询条件带下去
        AudienceScope scope = AudienceResolver.current();
        ProductService.ProductExportQuery query = new ProductService.ProductExportQuery(
                pName, proDesc, productionDateStart, productionDateEnd, origin, isExpired, scope);
        ExportTaskVO vo = exportTaskService.submit(query);
        return ResponseDto.success(vo);
    }

    /**
     * 查询异步导出任务状态。
     */
    @GetMapping("/export/status/{taskId}")
    public ResponseDto<Product> exportStatus(@PathVariable("taskId") String taskId) {
        ExportTaskVO vo = exportTaskService.status(taskId);
        if (vo == null) {
            return ResponseDto.error("任务不存在或已过期");
        }
        return ResponseDto.success(vo);
    }

    /**
     * 取消异步导出任务。
     */
    @GetMapping("/export/cancel/{taskId}")
    public ResponseDto<Product> exportCancel(@PathVariable("taskId") String taskId) {
        ExportTaskVO vo = exportTaskService.cancel(taskId);
        if (vo == null) {
            return ResponseDto.error("任务不存在或已过期");
        }
        return ResponseDto.success(vo);
    }

    /**
     * 下载已完成的导出文件。
     */
    @GetMapping("/export/download/{taskId}")
    public void exportDownload(@PathVariable("taskId") String taskId,
                               HttpServletResponse response) throws Exception {
        boolean ok = exportTaskService.download(taskId, response);
        if (!ok) {
            response.reset();
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":500,\"msg\":\"文件不存在或任务未完成\"}");
        }
    }

}
