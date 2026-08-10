package com.example.scuser.util;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.curry.model.OrderMessage;
import com.curry.model.User;
import com.example.scuser.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import response.ResponseDto;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.List;

/**
 * 邮件发送工具：纯文本、HTML 及按用户名反查邮箱发送。
 */
@Component
public class MailUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailUtil.class);

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

    /**
     * 发送 HTML 正文邮件（富文本内容场景，如工作日报/周报）。
     */
    public void sendHtmlTo(String to, String subject, String html) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(sendFrom);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        javaMailSender.send(message);
    }

    /**
     * 按用户名反查邮箱并发送邮件；用户不存在或未维护邮箱时返回错误提示。
     * @param orderMessage 收件人账号、主题与正文
     */
    public ResponseDto<Void> sendMail(OrderMessage orderMessage) {
        try {
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().eq(
                    User::getUName, orderMessage.getToAcc()
            ));
            User user = users.isEmpty() ? null : users.get(0);
            if (user == null || StringUtils.isBlank(user.getEmail())) {
                return ResponseDto.error("【" + orderMessage.getToAcc() + "】未维护邮箱信息");
            }

            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(sendFrom);
            mailMessage.setTo(user.getEmail());
            mailMessage.setSubject(orderMessage.getSubject());
            mailMessage.setText(orderMessage.getMessage());
            javaMailSender.send(mailMessage);
        } catch (Exception e) {
            LOGGER.error("邮件发送失败, toAcc={}", orderMessage.getToAcc(), e);
            return ResponseDto.error(e.getMessage());
        }
        return ResponseDto.success();
    }
}
