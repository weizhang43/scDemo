package com.example.scproduct.controller;

import com.curry.model.SeckillActivity;
import com.curry.model.annotation.OpLog;
import com.example.scproduct.auth.AudienceResolver;
import com.example.scproduct.service.SeckillActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

/**
 * 商品秒杀活动。挂在 /product/seckill 下以复用现有网关路由与前端代理。
 */
@RestController
@RequestMapping("/product/seckill")
public class SeckillActivityController {

    @Autowired
    private SeckillActivityService seckillActivityService;

    /** 商家端：秒杀活动分页列表，pId 非空时只看某个商品 */
    @GetMapping("/pageQuery")
    public ResponseDto<SeckillActivity> pageQuery(@RequestParam(value = "pId", required = false) Integer pId,
                                                  @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                  @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return seckillActivityService.pageQuery(pId, pageNo, pageSize, AudienceResolver.current());
    }

    /** 顾客端：尚未结束的有效活动（含未开始的，前端做倒计时） */
    @GetMapping("/active")
    public ResponseDto<SeckillActivity> active() {
        return seckillActivityService.listForCustomer();
    }

    /** 活动详情。sc-order 落库时取秒杀价与商品名快照 */
    @GetMapping("/detail/{id}")
    public ResponseDto<SeckillActivity> detail(@PathVariable("id") Integer id) {
        SeckillActivity activity = seckillActivityService.detail(id);
        if (activity == null) {
            return ResponseDto.error("秒杀活动不存在");
        }
        return ResponseDto.success(activity);
    }

    /** 发布秒杀活动 */
    @OpLog(module = "商品管理", type = OpLog.OpType.ADD, description = "发布秒杀活动")
    @PostMapping
    public ResponseDto<SeckillActivity> create(@RequestBody SeckillActivity activity) {
        return seckillActivityService.create(activity, AudienceResolver.current());
    }

    /** 取消秒杀活动 */
    @OpLog(module = "商品管理", type = OpLog.OpType.UPDATE, description = "取消秒杀活动")
    @DeleteMapping("/{id}")
    public ResponseDto<SeckillActivity> cancel(@PathVariable("id") Integer id) {
        return seckillActivityService.cancel(id, AudienceResolver.current());
    }

    /**
     * 秒杀预扣名额（Redis 原子，一人一单）。
     */
    @PostMapping("/preDeduct")
    public ResponseDto<SeckillActivity> preDeduct(@RequestParam("activityId") Integer activityId,
                                                  @RequestParam("uId") Integer uId) {
        return seckillActivityService.preDeduct(activityId, uId);
    }

    /**
     * 秒杀补偿：落库失败时回滚预扣。restoreStock=false 表示真实库存不足，只放开一人一单不归还名额。
     */
    @PostMapping("/rollback")
    public ResponseDto<SeckillActivity> rollback(@RequestParam("activityId") Integer activityId,
                                                 @RequestParam("uId") Integer uId,
                                                 @RequestParam(value = "restoreStock", defaultValue = "true")
                                                 boolean restoreStock) {
        return seckillActivityService.rollback(activityId, uId, restoreStock);
    }
}
