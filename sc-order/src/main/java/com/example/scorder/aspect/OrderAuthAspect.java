package com.example.scorder.aspect;

import com.curry.model.auth.AuthConstant;
import com.example.scorder.service.UserFeignService;
import exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import response.ResponseDto;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@Aspect
@Component
public class OrderAuthAspect {

    private static final String UPDATE_STATUS_PERMISSION = "order:updateStatus";
    private static final String MSG_NOT_LOGIN = "未登录";
    private static final String MSG_NO_PERMISSION = "无权执行该操作";

    @Autowired
    private UserFeignService userFeignService;

    @Around("execution(* com.example.scorder.service.impl.OrderServiceImpl.updateStatus(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return pjp.proceed();
        }
        HttpServletRequest request = attrs.getRequest();
        Integer uId = parseInt(request.getHeader(AuthConstant.HEADER_X_USER_ID));
        Integer uType = parseInt(request.getHeader(AuthConstant.HEADER_X_USER_TYPE));
        if (uId == null) {
            throw new BusinessException(MSG_NOT_LOGIN);
        }
        if (AuthConstant.U_TYPE_ADMIN == uType || hasPermission()) {
            return pjp.proceed();
        }
        log.warn("[OrderAuth] updateStatus denied, uId={}, uType={}", uId, uType);
        throw new BusinessException(MSG_NO_PERMISSION);
    }

    private boolean hasPermission() {
        ResponseDto<String> response = userFeignService.myPerms();
        if (response == null || !ResponseDto.SUCCESS_CODE.equals(response.getCode())) {
            return false;
        }
        List<String> perms = response.getDataList();
        return perms != null && perms.contains(UPDATE_STATUS_PERMISSION);
    }

    private Integer parseInt(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
