package com.example.scuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.PointsRecord;
import com.example.scuser.client.CouponIssueClient;
import com.example.scuser.mapper.PointsRecordMapper;
import com.example.scuser.service.PointsLevelRule;
import com.example.scuser.service.PointsService;
import exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员积分服务实现。
 * 加分幂等：t_points_record 唯一键 (u_id, order_no, type)，支付回调重试只加一次。
 * 兑换：先扣分后发券，发券失败写补偿流水把积分还回去（best-effort，Demo 不引入分布式事务）。
 */
@Service
public class PointsServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements PointsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PointsServiceImpl.class);

    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 兑换券有效期（天） */
    private static final int REDEEM_COUPON_VALID_DAYS = 30;

    @Autowired
    private PointsLevelRule pointsLevelRule;

    @Autowired
    private CouponIssueClient couponIssueClient;

    @Override
    public Map<String, Object> summary(Integer uId) {
        int balance = baseMapper.sumBalance(uId);
        int totalEarned = baseMapper.sumTotalEarned(uId);
        Map<String, Object> result = new HashMap<>();
        result.put("balance", balance);
        result.put("totalEarned", totalEarned);
        result.putAll(pointsLevelRule.resolve(totalEarned));
        List<Map<String, Object>> options = new ArrayList<>();
        for (String[] option : PointsLevelRule.REDEEM_OPTIONS) {
            Map<String, Object> item = new HashMap<>();
            item.put("key", option[0]);
            item.put("cost", Integer.valueOf(option[1]));
            item.put("threshold", option[2]);
            item.put("off", option[3]);
            item.put("name", option[4]);
            item.put("affordable", balance >= Integer.parseInt(option[1]));
            options.add(item);
        }
        result.put("redeemOptions", options);
        return result;
    }

    @Override
    public ResponseDto<PointsRecord> pageRecords(Integer uId, Integer pageNum, Integer pageSize) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        Page<PointsRecord> page = baseMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<PointsRecord>()
                        .eq(PointsRecord::getUId, uId)
                        .orderByDesc(PointsRecord::getId));
        return ResponseDto.success(page);
    }

    @Override
    public boolean earn(Integer uId, String orderNo, int points, String remark) {
        if (uId == null || orderNo == null || points <= 0) {
            LOGGER.warn("[Points] 加分参数非法 uId={}, orderNo={}, points={}", uId, orderNo, points);
            return false;
        }
        PointsRecord record = new PointsRecord();
        record.setUId(uId);
        record.setChangeValue(points);
        record.setType(PointsRecord.TYPE_EARN);
        record.setOrderNo(orderNo);
        record.setRemark(remark);
        record.setCreateTime(new Date());
        try {
            return baseMapper.insert(record) > 0;
        } catch (DuplicateKeyException e) {
            LOGGER.info("[Points] 重复加分忽略 uId={}, orderNo={}", uId, orderNo);
            return true;
        }
    }

    @Override
    public Map<String, Object> redeem(Integer uId, String optionKey) {
        String[] option = pointsLevelRule.findOption(optionKey);
        if (option == null) {
            throw new BusinessException("兑换选项不存在");
        }
        int cost = Integer.parseInt(option[1]);
        if (baseMapper.sumBalance(uId) < cost) {
            throw new BusinessException("积分不足，还差 " + (cost - baseMapper.sumBalance(uId)) + " 分");
        }
        // 先落扣分流水（redeemNo 唯一，天然防重复提交）
        String redeemNo = PointsLevelRule.newRedeemNo();
        insertRecord(uId, -cost, PointsRecord.TYPE_REDEEM, redeemNo, "兑换" + option[4]);
        try {
            ResponseDto<Object> issued = couponIssueClient.issue(uId, "积分兑换·" + option[4],
                    new BigDecimal(option[2]), new BigDecimal(option[3]), REDEEM_COUPON_VALID_DAYS);
            if (issued == null || !Integer.valueOf(200).equals(issued.getCode())) {
                throw new BusinessException("发券失败：" + (issued == null ? "无响应" : issued.getMsg()));
            }
        } catch (Exception e) {
            LOGGER.error("[Points] 兑换发券失败，补偿返还 uId={}, redeemNo={}", uId, redeemNo, e);
            insertRecord(uId, cost, PointsRecord.TYPE_COMPENSATE, redeemNo, "兑换失败返还");
            throw new BusinessException("兑换失败，积分已退回");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("balance", baseMapper.sumBalance(uId));
        result.put("couponName", option[4]);
        return result;
    }

    private void insertRecord(Integer uId, int changeValue, String type, String orderNo, String remark) {
        PointsRecord record = new PointsRecord();
        record.setUId(uId);
        record.setChangeValue(changeValue);
        record.setType(type);
        record.setOrderNo(orderNo);
        record.setRemark(remark);
        record.setCreateTime(new Date());
        baseMapper.insert(record);
    }
}
