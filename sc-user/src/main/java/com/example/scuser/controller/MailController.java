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

@RequestMapping(value = "/user/mail")
@RestController
public class MailController {
    @Autowired
    private MailUtil mailUtil;

    @PostMapping("/sendMail")
    public ResponseDto sendMail(@RequestBody @Validated OrderMessage orderMessage) {
        return mailUtil.sendMail(orderMessage);
    }
}
