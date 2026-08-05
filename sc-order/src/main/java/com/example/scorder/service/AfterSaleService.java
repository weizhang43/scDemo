package com.example.scorder.service;

import com.example.scorder.auth.OrderScope;
import com.example.scorder.dto.AfterSaleApplyRequest;
import com.example.scorder.entity.AfterSale;
import response.ResponseDto;

public interface AfterSaleService {

    /** 顾客申请售后：订单归属 + 状态(已完成/已发货)校验，一单一工单 */
    ResponseDto<AfterSale> apply(Integer uId, AfterSaleApplyRequest request);

    /** 顾客撤销待审核的售后申请 */
    ResponseDto<AfterSale> cancel(Integer uId, Integer id);

    /** 商家审核：同意则发起退款 + 回补库存，拒绝需填原因 */
    ResponseDto<AfterSale> audit(Integer id, boolean approve, String rejectReason, OrderScope scope);

    /** 我的售后列表（顾客） */
    ResponseDto<AfterSale> pageMine(Integer uId, Integer status, int pageNo, int pageSize);

    /** 售后工单列表（商家/管理员） */
    ResponseDto<AfterSale> pageAll(Integer status, int pageNo, int pageSize, OrderScope scope);

    /** 某订单的售后工单（前端按钮态用），无则 daoResult 为 null */
    ResponseDto<AfterSale> getByOrder(Integer oId, OrderScope scope);

    /** 重试"同意退款中"但退款未成功的工单，返回本次处理数量（sc-job 调用） */
    int retryRefund();
}
