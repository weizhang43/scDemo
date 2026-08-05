package com.example.scpay.notify;

import com.example.scpay.config.MockPayProperties;
import com.example.scpay.entity.MockPayTxn;
import com.example.scpay.mapper.MockPayTxnMapper;
import com.curry.model.pay.PaySignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 异步回调投递器：延迟首发，商户响应非 "success" 时按 [5s,15s,60s] 重试。
 * 每次发送（含重试/重发）都带新 nonce —— 商户侧防重放靠 nonce，业务幂等靠状态 CAS。
 */
@Slf4j
@Component
public class NotifySender {

    private static final long[] RETRY_DELAYS_MS = {5_000L, 15_000L, 60_000L};

    @Autowired
    private ThreadPoolTaskScheduler notifyScheduler;

    @Autowired
    private MockPayProperties properties;

    @Autowired
    private MockPayTxnMapper txnMapper;

    @Value("${pay.secret}")
    private String paySecret;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 支付结果确定后调度首发回调；notify-duplicate 打开时故意双发。
     */
    public void scheduleNotify(MockPayTxn txn, String tradeStatus) {
        long delay = properties.getNotifyDelayMs();
        schedule(txn, tradeStatus, delay, 0);
        if (properties.isNotifyDuplicate()) {
            schedule(txn, tradeStatus, delay + 500, 0);
        }
    }

    private void schedule(MockPayTxn txn, String tradeStatus, long delayMs, int attempt) {
        notifyScheduler.schedule(() -> send(txn, tradeStatus, attempt),
                Instant.now().plusMillis(delayMs));
    }

    private void send(MockPayTxn txn, String tradeStatus, int attempt) {
        String result;
        try {
            Map<String, String> params = new HashMap<>();
            params.put("payNo", txn.getPayNo());
            params.put("transactionId", txn.getTransactionId());
            params.put("tradeStatus", tradeStatus);
            params.put("amount", txn.getAmount().toPlainString());
            params.put("timestamp", String.valueOf(System.currentTimeMillis()));
            params.put("nonce", UUID.randomUUID().toString().replace("-", ""));
            params.put("sign", PaySignUtil.sign(params, paySecret));

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            params.forEach(form::add);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            result = restTemplate.postForObject(txn.getNotifyUrl(),
                    new HttpEntity<>(form, headers), String.class);
        } catch (Exception e) {
            log.warn("[NotifySender] 回调发送异常 txn={}, attempt={}, err={}",
                    txn.getTransactionId(), attempt, e.getMessage());
            result = "error:" + e.getClass().getSimpleName();
        }

        String recorded = result == null ? "null" : result;
        if (recorded.length() > 60) {
            recorded = recorded.substring(0, 60);
        }
        txnMapper.recordNotifyResult(txn.getTransactionId(), recorded);
        log.info("[NotifySender] 回调完成 txn={}, tradeStatus={}, attempt={}, resp={}",
                txn.getTransactionId(), tradeStatus, attempt, recorded);

        if (!"success".equals(result) && attempt < RETRY_DELAYS_MS.length) {
            schedule(txn, tradeStatus, RETRY_DELAYS_MS[attempt], attempt + 1);
        }
    }
}
