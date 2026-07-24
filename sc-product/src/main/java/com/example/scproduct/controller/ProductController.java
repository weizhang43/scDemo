package com.example.scproduct.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.curry.model.Product;
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
        return productService.listExpiringSoon();
    }

    /** 首页预警：库存不足的商品（阈值默认 100） */
    @GetMapping("/warning/lowStock")
    public ResponseDto<Product> lowStock(@RequestParam(value = "threshold", defaultValue = "100") Integer threshold) {
        return productService.listLowStock(threshold);
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
     * 按主键查询商品。
     */
    @GetMapping("/{id}")
    public Product get(@PathVariable("id") Integer id) {
        return productService.getById(id);
    }

    /**
     * 查询全部商品列表。
     */
    @GetMapping("/list")
    public List<Product> list() {
        return productService.list();
    }

    /**
     * 根据 ID 列表批量查商品
     */
    @GetMapping("/listByIds")
    public ResponseDto<Product> listByIds(@RequestParam("ids") List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseDto.success(null);
        }
        return ResponseDto.success(productService.listByIds(ids));
    }

    /**
     * 按主键更新商品信息。
     */
    @PutMapping
    public boolean update(@RequestBody Product product) {
        return productService.updateById(product);
    }

    /**
     * 按主键删除商品。
     */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable("id") Integer id) {
        return productService.removeById(id);
    }


    /**
     * 商品查询（简易版）：按关键字、价格等过滤分页返回。
     */
    @GetMapping("/queryProduct")
    public ResponseDto<Product> queryProduct(String key,int price,int pageNo,int pageSize) {
        return productService.queryProduct(key,price,pageNo,pageSize);
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
        return productService.pageQuery(pName, proDesc, productionDateStart, productionDateEnd, origin, isExpired, pageNo, pageSize);
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
    @PostMapping("/add")
    public ResponseDto<Product> add(@RequestBody Product product) {
        return productService.addOne(product);
    }

    /**
     * 商品点赞：点赞数量 +1
     */
    @PostMapping("/like/{id}")
    public ResponseDto<Product> like(@PathVariable("id") Integer id) {
        return productService.like(id);
    }

    /**
     * 秒杀预扣库存（Redis 原子，一人一单）
     */
    @PostMapping("/seckill/preDeduct")
    public ResponseDto<Product> seckillPreDeduct(@RequestParam("pId") Integer pId,
                                                 @RequestParam("uId") Integer uId) {
        return productService.seckillPreDeduct(pId, uId);
    }

    /**
     * 秒杀补偿：回滚 Redis 预扣库存（落库失败时调用）
     */
    @PostMapping("/seckill/rollback")
    public ResponseDto<Product> seckillRollback(@RequestParam("pId") Integer pId,
                                                @RequestParam("uId") Integer uId) {
        return productService.rollbackSeckillStock(pId, uId);
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
            return productService.addStock(products);
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
        productService.export(pName, proDesc, productionDateStart, productionDateEnd, origin, isExpired, response);
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
        ProductService.ProductExportQuery query = new ProductService.ProductExportQuery(
                pName, proDesc, productionDateStart, productionDateEnd, origin, isExpired);
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
