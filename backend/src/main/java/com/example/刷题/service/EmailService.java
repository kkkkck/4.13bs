package com.example.刷题.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.auth.verification-code-expire-seconds:300}")
    private int verificationCodeExpireSeconds;

    public boolean canSendMail() {
        return mailEnabled && StringUtils.hasText(from);
    }

    public void sendVerificationCode(String to, String code) {
        if (!canSendMail()) {
            log.info("Mail disabled. verificationCode={}, email={}", code, to);
            return;
        }

        int expireMinutes = Math.max(1, verificationCodeExpireSeconds / 60);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("邮箱验证码");
        message.setText("您的验证码是 " + code + "，将在 " + expireMinutes + " 分钟后失效。");

        try {
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send verification mail", ex);
            throw new RuntimeException("Failed to send email");
        }
    }
}
