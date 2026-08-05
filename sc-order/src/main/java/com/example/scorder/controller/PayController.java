package com.example.scorder.controller;

import com.example.scorder.auth.OrderScopeResolver;
import com.example.scorder.dto.PayCreateRequest;
import com.example.scorder.service.PayService;
import com.example.scorder.vo.PayCreateVO;
import com.example.scorder.vo.PayStatusVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

import java.util.Map;

@RestController
@RequestMapping("/order/pay")
public class PayController {

    @Autowired
    private PayService payService;

    /**
     * 创建支付单：返回 payNo + 收银台跳转地址。
     */
    @PostMapping("/create")
    public ResponseDto<PayCreateVO> create(@RequestBody PayCreateRequest request) {
        return payService.createPay(request.getOId(), request.getChannel(), OrderScopeResolver.current());
    }

    /**
     * 支付单状态轮询。
     */
    @GetMapping("/status/{payNo}")
    public ResponseDto<PayStatusVO> status(@PathVariable("payNo") String payNo) {
        return payService.getStatus(payNo, OrderScopeResolver.current());
    }

    /**
     * 网关异步回调入口（白名单放行，安全靠 HMAC 验签 + nonce 防重放）。
     * 按网关协议返回 text/plain："success" 不再重试，其余按退避重发。
     */
    @PostMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(@RequestParam Map<String, String> params) {
        return payService.handleNotify(params);
    }
}
