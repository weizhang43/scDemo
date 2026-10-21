package com.example.scuser.controller;

import com.curry.model.PointsRecord;
import com.curry.model.auth.AuthConstant;
import com.example.scuser.service.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

/**
 * 会员积分接口。/earn 为服务间内部接口（sc-order 支付成功后经 Feign 调用）。
 */
@RestController
@RequestMapping("/user/points")
public class PointsController {

    @Autowired
    private PointsService pointsService;

    @Value("${inner.auth.token:}")
    private String innerToken;

    /** 积分余额/等级/兑换选项汇总 */
    @GetMapping("/summary")
    public ResponseDto<Object> summary(@RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        if (uId == null) {
            return ResponseDto.error("请先登录");
        }
        return ResponseDto.success(pointsService.summary(uId));
    }

    /** 积分明细分页 */
    @GetMapping("/records")
    public ResponseDto<PointsRecord> records(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                             @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                             @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        if (uId == null) {
            return ResponseDto.error("请先登录");
        }
        return pointsService.pageRecords(uId, pageNum, pageSize);
    }

    /** 积分兑换优惠券 */
    @PostMapping("/redeem")
    public ResponseDto<Object> redeem(@RequestParam("optionKey") String optionKey,
                                      @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        if (uId == null) {
            return ResponseDto.error("请先登录");
        }
        return ResponseDto.success(pointsService.redeem(uId, optionKey));
    }

    /**
     * 服务间加分入口：需内部令牌或真实用户上下文（同 MessageController 策略）。
     */
    @PostMapping("/earn")
    public ResponseDto<Object> earn(@RequestParam("uId") Integer uId,
                                    @RequestParam("orderNo") String orderNo,
                                    @RequestParam("points") Integer points,
                                    @RequestParam(value = "remark", required = false) String remark,
                                    @RequestHeader(value = AuthConstant.HEADER_X_INNER_TOKEN, required = false) String inner,
                                    @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer callerUId) {
        boolean innerOk = innerToken != null && !innerToken.isEmpty() && innerToken.equals(inner);
        if (!innerOk && callerUId == null) {
            return ResponseDto.error("无权限");
        }
        boolean ok = pointsService.earn(uId, orderNo, points == null ? 0 : points, remark);
        return ok ? ResponseDto.success(null) : ResponseDto.error("加分失败");
    }
}
