package com.example.scuser.util;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.curry.model.OrderMessage;
import com.curry.model.User;
import com.example.scuser.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import response.ResponseDto;

import java.util.List;

@Component
@Slf4j
public class MailUtil {

    @Autowired
    private JavaMailSender javaMailSender;

    // 用 mapper 而非 UserService：UserServiceImpl 需要注入 MailUtil 发注册验证码，
    // 若这里依赖 UserService 就会构成循环依赖（Boot 2.5 默认已禁止循环引用）
    @Autowired
    private UserMapper userMapper;

    @Value("${spring.mail.username}")
    private String sendFrom;

    /**
     * 按原始收件地址发信。注册场景下用户还没落库，无法像 sendMail 那样按 uName 反查邮箱。
     */
    public void sendTo(String to, String subject, String text) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(sendFrom);
        mailMessage.setTo(to);
        mailMessage.setSubject(subject);
        mailMessage.setText(text);
        javaMailSender.send(mailMessage);
    }

    public ResponseDto sendMail(OrderMessage orderMessage) {
        try {
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().eq(
                    User::getUName, orderMessage.getToAcc()
            ));
            User user = users.isEmpty() ? null : users.get(0);
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
            log.error("邮件发送失败, toAcc={}", orderMessage.getToAcc(), e);
            return ResponseDto.error(e.getMessage());
        }
        return ResponseDto.success();
    }
}
