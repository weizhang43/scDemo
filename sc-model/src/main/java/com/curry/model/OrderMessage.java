package com.curry.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
public class OrderMessage {
    /**
     * 收件人账号
     */
    @NotBlank(message = "收件人账号不能为空")
    private String toAcc;
    /**
     * 邮件主题
     */
    @NotBlank(message = "邮件主题不能为空")
    private String subject;
    /**
     * 邮件内容
     */
    @NotBlank(message = "邮件内容不能为空")
    private String message;


    public OrderMessage(String toAcc, String subject, String message){
        this.toAcc = toAcc;
        this.subject = subject;
        this.message = message;
    }
}
