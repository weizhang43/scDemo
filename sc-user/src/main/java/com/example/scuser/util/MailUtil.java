package com.example.scuser.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.curry.model.OrderMessage;
import com.curry.model.User;
import com.example.scuser.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import response.ResponseDto;

@Component
@Slf4j
public class MailUtil {

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private UserService userService;

    @Value("${spring.mail.username}")
    private String sendFrom;

    public ResponseDto seneMail(OrderMessage orderMessage) {
        try {
            User user = userService.getOne(new LambdaQueryWrapper<User>().eq(
                    User::getUName, orderMessage.getToAcc()
            ));
            if (user == null || StringUtils.isBlank(user.getEmail())) {
                return ResponseDto.error("【"+orderMessage.getToAcc()+"】未维护邮箱信息" );
            }

            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(sendFrom);
            mailMessage.setTo(user.getEmail());
            mailMessage.setSubject(orderMessage.getSubject());
            mailMessage.setText(orderMessage.getMessage());
            javaMailSender.send(mailMessage);
        } catch (Exception e) {
            log.error("程序执行报错，报错信息：{}", e.getMessage());
            return ResponseDto.error(e.getMessage());
        }
        return ResponseDto.success();
    }
}
