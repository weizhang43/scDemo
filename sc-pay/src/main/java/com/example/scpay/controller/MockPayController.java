package com.example.scpay.controller;

import com.example.scpay.entity.MockPayTxn;
import com.example.scpay.service.MockPayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

import java.util.Map;

/**
 * 模拟支付网关接口。
 * 商户接口（precreate/query/closeTxn/refund）走 HMAC 验签；
 * 收银台接口（txn/simulate/renotify）供前端裸访问，网关白名单放行。
 */
@RestController
@RequestMapping("/pay")
public class MockPayController {

    @Autowired
    private MockPayService mockPayService;

    /** 商户预下单：payNo 幂等，返回交易单（含 transactionId） */
    @PostMapping("/precreate")
    public ResponseDto<MockPayTxn> precreate(@RequestParam Map<String, String> params) {
        return ResponseDto.success(mockPayService.precreate(params));
    }

    /** 商户查单 */
    @GetMapping("/query")
    public ResponseDto<MockPayTxn> query(@RequestParam Map<String, String> params) {
        MockPayTxn txn = mockPayService.query(params);
        if (txn == null) {
            return ResponseDto.error("交易单不存在");
        }
        return ResponseDto.success(txn);
    }

    /** 商户关单：CAS 0→3，幂等 */
    @PostMapping("/closeTxn")
    public ResponseDto<MockPayTxn> closeTxn(@RequestParam Map<String, String> params) {
        return ResponseDto.success(mockPayService.closeTxn(params));
    }

    /** 商户退款：记账 */
    @PostMapping("/refund")
    public ResponseDto<MockPayTxn> refund(@RequestParam Map<String, String> params) {
        return ResponseDto.success(mockPayService.refund(params));
    }

    /** 收银台：展示交易单（金额/摘要/状态） */
    @GetMapping("/txn/{transactionId}")
    public ResponseDto<MockPayTxn> txn(@PathVariable("transactionId") String transactionId) {
        MockPayTxn txn = mockPayService.getByTransactionId(transactionId);
        if (txn == null) {
            return ResponseDto.error("交易单不存在");
        }
        return ResponseDto.success(txn);
    }

    /** 收银台：模拟支付结果，触发异步回调 */
    @PostMapping("/simulate")
    public ResponseDto<MockPayTxn> simulate(@RequestBody Map<String, String> body) {
        return ResponseDto.success(
                mockPayService.simulate(body.get("transactionId"), body.get("result")));
    }

    /** 手动重发回调（演示商户侧幂等） */
    @PostMapping("/renotify/{transactionId}")
    public ResponseDto<MockPayTxn> renotify(@PathVariable("transactionId") String transactionId) {
        return ResponseDto.success(mockPayService.renotify(transactionId));
    }
}
