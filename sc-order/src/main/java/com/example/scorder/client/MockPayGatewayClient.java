package com.example.scorder.client;

import com.curry.model.pay.PaySignUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 模拟支付网关商户客户端：刻意不走 Feign，用 RestTemplate 穿网关 HTTP，
 * 模拟真实第三方支付的外部往返。请求走 HMAC-SHA256 签名（PaySignUtil）。
 */
@Slf4j
@Component
public class MockPayGatewayClient {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${pay.gateway.base-url:http://localhost:8000/pay}")
    private String baseUrl;

    @Value("${pay.secret:sc-pay-secret-dev}")
    private String paySecret;

    @Value("${pay.notify-url:http://localhost:8000/order/pay/notify}")
    private String notifyUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 预下单：返回网关交易号 transactionId。失败抛 BusinessException。
     */
    public String precreate(String payNo, BigDecimal amount, String subject) {
        Map<String, String> params = baseParams();
        params.put("payNo", payNo);
        params.put("amount", amount.toPlainString());
        params.put("subject", subject);
        params.put("notifyUrl", notifyUrl);
        JsonNode dao = call("/precreate", params);
        JsonNode txnId = dao.get("transactionId");
        if (txnId == null || txnId.asText().isEmpty()) {
            throw new BusinessException("支付网关未返回交易号");
        }
        return txnId.asText();
    }

    /**
     * 关单（best-effort）：异常只记日志，交易单留在网关侧待支付也无碍——支付单已本地关闭，回调会被拒。
     */
    public boolean closeTxn(String transactionId) {
        try {
            Map<String, String> params = baseParams();
            params.put("transactionId", transactionId);
            call("/closeTxn", params);
            return true;
        } catch (Exception e) {
            log.warn("[MockPayGatewayClient] 关单失败 txn={}, err={}", transactionId, e.getMessage());
            return false;
        }
    }

    /**
     * 退款（best-effort）：调用方按返回值决定支付单是否推进到已退款。
     */
    public boolean refund(String transactionId) {
        try {
            Map<String, String> params = baseParams();
            params.put("transactionId", transactionId);
            call("/refund", params);
            return true;
        } catch (Exception e) {
            log.warn("[MockPayGatewayClient] 退款失败 txn={}, err={}", transactionId, e.getMessage());
            return false;
        }
    }

    private Map<String, String> baseParams() {
        Map<String, String> params = new HashMap<>();
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        params.put("nonce", UUID.randomUUID().toString().replace("-", ""));
        return params;
    }

    /**
     * 签名并 POST form 到网关，code!=200 抛 BusinessException，返回 daoResult 节点。
     */
    private JsonNode call(String path, Map<String, String> params) {
        params.put("sign", PaySignUtil.sign(params, paySecret));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String raw;
        try {
            raw = restTemplate.postForObject(baseUrl + path, new HttpEntity<>(form, headers), String.class);
        } catch (Exception e) {
            log.error("[MockPayGatewayClient] 网关调用失败 path={}, err={}", path, e.getMessage());
            throw new BusinessException("支付网关暂不可用，请稍后重试");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.get("code") == null || root.get("code").asInt() != 200) {
                String msg = root.get("msg") == null ? "未知错误" : root.get("msg").asText();
                throw new BusinessException("支付网关返回失败：" + msg);
            }
            JsonNode dao = root.get("daoResult");
            return dao == null ? objectMapper.createObjectNode() : dao;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[MockPayGatewayClient] 网关响应解析失败 path={}, raw={}", path, raw);
            throw new BusinessException("支付网关响应异常");
        }
    }
}
