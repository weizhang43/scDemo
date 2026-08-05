package com.example.scmodel;

import com.curry.model.pay.PaySignUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaySignUtilTest {

    private static final String SECRET = "test-secret";

    private Map<String, String> baseParams() {
        Map<String, String> p = new HashMap<>();
        p.put("payNo", "PAY20260804000001");
        p.put("amount", "199.00");
        p.put("timestamp", "1770000000000");
        p.put("nonce", "abc123");
        return p;
    }

    @Test
    void signVerifyRoundTrip() {
        Map<String, String> p = baseParams();
        p.put("sign", PaySignUtil.sign(p, SECRET));
        assertTrue(PaySignUtil.verify(p, SECRET));
    }

    @Test
    void tamperedParamFailsVerify() {
        Map<String, String> p = baseParams();
        p.put("sign", PaySignUtil.sign(p, SECRET));
        p.put("amount", "0.01");
        assertFalse(PaySignUtil.verify(p, SECRET));
    }

    @Test
    void wrongSecretFailsVerify() {
        Map<String, String> p = baseParams();
        p.put("sign", PaySignUtil.sign(p, SECRET));
        assertFalse(PaySignUtil.verify(p, "other-secret"));
    }

    @Test
    void missingSignFailsVerify() {
        assertFalse(PaySignUtil.verify(baseParams(), SECRET));
        assertFalse(PaySignUtil.verify(null, SECRET));
    }

    @Test
    void emptyValuesAndSignExcludedFromSigning() {
        Map<String, String> p = baseParams();
        String s1 = PaySignUtil.sign(p, SECRET);
        p.put("memo", "");
        p.put("extra", null);
        p.put("sign", "should-not-affect");
        assertEquals(s1, PaySignUtil.sign(p, SECRET));
    }
}
