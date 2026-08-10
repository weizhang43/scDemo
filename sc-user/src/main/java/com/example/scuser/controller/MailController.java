package com.example.scuser.controller;

import com.curry.model.OrderMessage;
import com.example.scuser.util.MailUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

/**
 * 邮件发送接口。
 */
@RequestMapping(value = "/user/mail")
@RestController
public class MailController {
    @Autowired
    private MailUtil mailUtil;

    /**
     * 按用户名反查邮箱并发送邮件。
     * @param orderMessage 收件人账号、主题与正文
     */
    @PostMapping("/sendMail")
    public ResponseDto<Void> sendMail(@RequestBody @Validated OrderMessage orderMessage) {
        return mailUtil.sendMail(orderMessage);
    }
}
