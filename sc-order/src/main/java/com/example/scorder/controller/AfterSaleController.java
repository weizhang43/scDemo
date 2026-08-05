package com.example.scorder.controller;

import com.curry.model.annotation.OpLog;
import com.curry.model.auth.AuthConstant;
import com.example.scorder.auth.OrderScopeResolver;
import com.example.scorder.dto.AfterSaleApplyRequest;
import com.example.scorder.entity.AfterSale;
import com.example.scorder.service.AfterSaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

/**
 * 售后工单。申请人身份取自网关注入的 X-User-Id，请求体里没有 uId。
 * 挂在 /order/aftersale 下复用网关既有的 Path=/order/** 路由与前端 devServer 代理（同 ReviewController）。
 */
@RestController
@RequestMapping("/order/aftersale")
public class AfterSaleController {

    @Autowired
    private AfterSaleService afterSaleService;

    /**
     * 顾客申请售后：已发货/已完成的订单整单退货退款。
     */
    @OpLog(module = "售后管理", type = OpLog.OpType.ADD, description = "申请售后")
    @PostMapping("/apply")
    public ResponseDto<AfterSale> apply(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestBody AfterSaleApplyRequest request) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return afterSaleService.apply(uId, request);
    }

    /**
     * 顾客撤销待审核的售后申请。
     */
    @OpLog(module = "售后管理", type = OpLog.OpType.UPDATE, description = "撤销售后申请")
    @PostMapping("/cancel/{id}")
    public ResponseDto<AfterSale> cancel(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @PathVariable("id") Integer id) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return afterSaleService.cancel(uId, id);
    }

    /**
     * 商家审核：approve=true 同意（发起退款+回补库存），false 拒绝（须填原因）。
     */
    @OpLog(module = "售后管理", type = OpLog.OpType.UPDATE, description = "审核售后申请")
    @PostMapping("/audit")
    public ResponseDto<AfterSale> audit(@RequestParam("id") Integer id,
                                        @RequestParam("approve") boolean approve,
                                        @RequestParam(value = "rejectReason", required = false) String rejectReason) {
        return afterSaleService.audit(id, approve, rejectReason, OrderScopeResolver.current());
    }

    /**
     * 我的售后列表（顾客）。
     */
    @GetMapping("/mine")
    public ResponseDto<AfterSale> mine(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return afterSaleService.pageMine(uId, status, pageNo, pageSize);
    }

    /**
     * 售后工单列表（商家/管理员），可按状态过滤。
     */
    @GetMapping("/list")
    public ResponseDto<AfterSale> list(@RequestParam(value = "status", required = false) Integer status,
                                       @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                       @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return afterSaleService.pageAll(status, pageNo, pageSize, OrderScopeResolver.current());
    }

    /**
     * 某订单的售后工单，供前端渲染"申请售后"按钮态；无工单时 daoResult 为 null。
     */
    @GetMapping("/order/{oId}")
    public ResponseDto<AfterSale> getByOrder(@PathVariable("oId") Integer oId) {
        return afterSaleService.getByOrder(oId, OrderScopeResolver.current());
    }
}
