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
import com.example.scorder.util.ImageUrlUtil;
import com.example.scorder.vo.ProductReviewSummaryVO;
import exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 商品评价服务实现：已完成订单内的商品可评价（唯一键防重复），按商品/按人分页查询与均分统计。
 */
@Service
public class ReviewServiceImpl extends ServiceImpl<ProductReviewMapper, ProductReview> implements ReviewService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewServiceImpl.class);

    /** 订单终态：已完成，只有这个状态能评价 */
    private static final Integer COMPLETE_ORDER_STATUS = 2;

    /** 评论内容最大长度 */
    private static final int CONTENT_MAX_LENGTH = 500;

    /** 评分下限（1 星） */
    private static final int RATING_MIN = 1;

    /** 评分上限（5 星） */
    private static final int RATING_MAX = 5;

    /** 评价图片最多张数 */
    private static final int IMAGES_MAX_COUNT = 3;

    @Autowired
    private ProductReviewMapper reviewMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    /**
     * 提交评价：校验参数与订单归属/状态后落库，唯一键 uk_o_id_p_id 兜底防重复评价。
     */
    @Override
    public ResponseDto<ProductReview> submitOwn(Integer uId, ReviewSubmitRequest req) {
        validateSubmitParams(req);
        OrderItem item = loadReviewableItem(uId, req);
        String content = req.getContent() == null ? null : req.getContent().trim();
        ProductReview review = buildReview(uId, req, item, content);
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // 唯一键 uk_o_id_p_id 是权威判据，不做先查后插
            LOGGER.info("重复评价被唯一键拦截 uId={}, oId={}, pId={}", uId, req.getOId(), req.getPId(), e);
            return ResponseDto.error("您已评价过该商品");
        }
        return ResponseDto.success(null);
    }

    /**
     * 校验评价入参：订单/商品ID、星级范围、内容长度，不合规抛业务异常。
     */
    private void validateSubmitParams(ReviewSubmitRequest req) {
        if (req == null || req.getOId() == null || req.getPId() == null) {
            throw new BusinessException("订单ID与商品ID不能为空");
        }
        Integer rating = req.getRating();
        if (rating == null || rating < RATING_MIN || rating > RATING_MAX) {
            throw new BusinessException("请选择 " + RATING_MIN + "-" + RATING_MAX + " 星");
        }
        String content = req.getContent() == null ? null : req.getContent().trim();
        if (content != null && content.length() > CONTENT_MAX_LENGTH) {
            throw new BusinessException("评论内容不能超过 " + CONTENT_MAX_LENGTH + " 字");
        }
    }

    /**
     * 校验订单归属与已完成状态，并确认商品确实在该订单内；通过返回订单明细（取名称快照）。
     */
    private OrderItem loadReviewableItem(Integer uId, ReviewSubmitRequest req) {
        Order order = orderMapper.selectById(req.getOId());
        // 越权与不存在给同一句提示，不向外暴露他人订单是否存在
        if (order == null || !uId.equals(order.getUId())) {
            throw new BusinessException("订单不存在");
        }
        if (!COMPLETE_ORDER_STATUS.equals(order.getOrderStatus())) {
            throw new BusinessException("订单完成后才能评价");
        }
        OrderItem item = orderItemMapper.selectOne(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOId, req.getOId())
                .eq(OrderItem::getPId, req.getPId())
                .last("LIMIT 1"));
        if (item == null) {
            throw new BusinessException("该商品不在此订单中");
        }
        return item;
    }

    /**
     * 组装评价记录，商品名取订单明细快照，空白内容落 null。
     */
    private ProductReview buildReview(Integer uId, ReviewSubmitRequest req, OrderItem item, String content) {
        ProductReview review = new ProductReview();
        review.setUId(uId);
        review.setOId(req.getOId());
        review.setPId(req.getPId());
        review.setPName(item.getPName());
        review.setRating(req.getRating());
        review.setContent(content == null || content.isEmpty() ? null : content);
        review.setImages(ImageUrlUtil.validateAndJoin(req.getImages(), IMAGES_MAX_COUNT));
        review.setCreateTime(new Date());
        return review;
    }

    /**
     * 按商品分页查询评价并附带平均星级。
     */
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

    /**
     * 分页查询本人发表的评价。
     */
    @Override
    public ResponseDto<ProductReview> listMine(Integer uId, int pageNo, int pageSize) {
        Page<ProductReview> page = new Page<>(pageNo, pageSize);
        IPage<ProductReview> result = reviewMapper.selectPage(page, new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getUId, uId)
                .orderByDesc(ProductReview::getCreateTime)
                .orderByDesc(ProductReview::getId));
        return ResponseDto.success(result);
    }

    /**
     * 查询本人某订单下已评价过的商品ID列表（前端置灰"去评价"按钮）。
     */
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
