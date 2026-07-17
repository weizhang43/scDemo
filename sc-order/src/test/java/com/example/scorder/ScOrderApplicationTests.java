package com.example.scorder;

import com.curry.model.auth.AuthConstant;
import com.example.scorder.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest
class ScOrderApplicationTests {
    @Autowired
    private OrderService orderService;

    @Test
    void addOrder() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthConstant.HEADER_X_USER_ID, "1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            orderService.addOrder();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

}
