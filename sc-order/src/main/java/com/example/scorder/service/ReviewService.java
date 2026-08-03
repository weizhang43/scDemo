package com.example.scorder.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.scorder.dto.ReviewSubmitRequest;
import com.example.scorder.entity.ProductReview;
import com.example.scorder.vo.ProductReviewSummaryVO;
import response.ResponseDto;

/**
 * 商品评价。uId 一律由 Controller 从网关注入的 X-User-Id 传入，
 * ...Own 后缀表示方法内做了归属校验。
 */
public interface ReviewService extends IService<ProductReview> {

    /**
     * 发表评价：校验订单归属、订单已完成、商品确实在这单里，按 (o_id, p_id) 唯一。
     * 只落 uId，展示名由查询侧联 t_user 现取。
     */
    ResponseDto<ProductReview> submitOwn(Integer uId, ReviewSubmitRequest req);

    /**
     * 某商品的评价列表（含平均分与总条数），顾客与商家读同一份。
     */
    ResponseDto<ProductReviewSummaryVO> listByProduct(Integer pId, int pageNo, int pageSize);

    /**
     * 我的历史评价，按时间倒序分页。
     */
    ResponseDto<ProductReview> listMine(Integer uId, int pageNo, int pageSize);

    /**
     * 某订单里已评过的商品 pId 列表，供前端把已评行置灰。先校订单归属。
     */
    ResponseDto<Integer> listReviewedPIdsOwn(Integer uId, Integer oId);
}
