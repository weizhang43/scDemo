package com.example.scorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Order;
import com.curry.model.OrderItem;
import com.example.scorder.dto.ReviewSubmitRequest;
import com.example.scorder.entity.ProductReview;
import com.example.scorder.mapper.OrderItemMapper;
import com.example.scorder.mapper.OrderMapper;
import com.example.scorder.mapper.ProductReviewMapper;
import com.example.scorder.service.ReviewService;
import com.example.scorder.vo.ProductReviewSummaryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class ReviewServiceImpl extends ServiceImpl<ProductReviewMapper, ProductReview> implements ReviewService {

    /** 订单终态：已完成，只有这个状态能评价 */
    private static final Integer COMPLETE_ORDER_STATUS = 2;
    private static final int CONTENT_MAX_LENGTH = 500;

    @Autowired
    private ProductReviewMapper reviewMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Override
    public ResponseDto<ProductReview> submitOwn(Integer uId, ReviewSubmitRequest req) {
        if (req == null || req.getOId() == null || req.getPId() == null) {
            return ResponseDto.error("订单ID与商品ID不能为空");
        }
        Integer rating = req.getRating();
        if (rating == null || rating < 1 || rating > 5) {
            return ResponseDto.error("请选择 1-5 星");
        }
        String content = req.getContent() == null ? null : req.getContent().trim();
        if (content != null && content.length() > CONTENT_MAX_LENGTH) {
            return ResponseDto.error("评论内容不能超过 " + CONTENT_MAX_LENGTH + " 字");
        }

        Order order = orderMapper.selectById(req.getOId());
        // 越权与不存在给同一句提示，不向外暴露他人订单是否存在
        if (order == null || !uId.equals(order.getUId())) {
            return ResponseDto.error("订单不存在");
        }
        if (!COMPLETE_ORDER_STATUS.equals(order.getOrderStatus())) {
            return ResponseDto.error("订单完成后才能评价");
        }
        OrderItem item = orderItemMapper.selectOne(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOId, req.getOId())
                .eq(OrderItem::getPId, req.getPId())
                .last("LIMIT 1"));
        if (item == null) {
            return ResponseDto.error("该商品不在此订单中");
        }

        ProductReview review = new ProductReview();
        review.setUId(uId);
        review.setOId(req.getOId());
        review.setPId(req.getPId());
        review.setPName(item.getPName());
        review.setRating(rating);
        review.setContent(content == null || content.isEmpty() ? null : content);
        review.setCreateTime(new Date());
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // 唯一键 uk_o_id_p_id 是权威判据，不做先查后插
            return ResponseDto.error("您已评价过该商品");
        }
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<ProductReviewSummaryVO> listByProduct(Integer pId, int pageNo, int pageSize) {
        if (pId == null) {
            return ResponseDto.error("商品ID不能为空");
        }
        Page<ProductReview> page = new Page<>(pageNo, pageSize);
        IPage<ProductReview> result = reviewMapper.selectPageByProduct(page, pId);
        BigDecimal avg = reviewMapper.selectAvgRating(pId);
        ProductReviewSummaryVO vo = new ProductReviewSummaryVO();
        vo.setAvgRating(avg == null ? BigDecimal.ZERO : avg);
        vo.setTotal(result.getTotal());
        vo.setRecords(result.getRecords());
        return ResponseDto.success(vo);
    }

    @Override
    public ResponseDto<ProductReview> listMine(Integer uId, int pageNo, int pageSize) {
        Page<ProductReview> page = new Page<>(pageNo, pageSize);
        IPage<ProductReview> result = reviewMapper.selectPage(page, new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getUId, uId)
                .orderByDesc(ProductReview::getCreateTime)
                .orderByDesc(ProductReview::getId));
        return ResponseDto.success(result);
    }

    @Override
    public ResponseDto<Integer> listReviewedPIdsOwn(Integer uId, Integer oId) {
        if (oId == null) {
            return ResponseDto.error("订单ID不能为空");
        }
        Order order = orderMapper.selectById(oId);
        if (order == null || !uId.equals(order.getUId())) {
            return ResponseDto.error("订单不存在");
        }
        List<Integer> pIds = reviewMapper.selectReviewedPIds(oId);
        return ResponseDto.success(pIds == null ? Collections.emptyList() : pIds);
    }
}
