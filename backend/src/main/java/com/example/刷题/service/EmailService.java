package com.example.刷题.service;

import com.example.刷题.exception.BusinessException;
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

    // 邮箱服务只负责“真的把验证码发出去”。验证码的生成、保存、校验在 VerificationCodeService。
    // 生产/本地都通过环境变量配置 SMTP，避免把 QQ 邮箱授权码写进 application.yml。
    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String from;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${app.mail.debug-code-enabled:false}")
    private boolean debugCodeEnabled;

    @Value("${app.auth.verification-code-expire-seconds:300}")
    private int verificationCodeExpireSeconds;

    public boolean canSendMail() {
        // true 表示当前配置足够完整，可以真实发送邮件。
        return mailEnabled && StringUtils.hasText(from);
    }

    public boolean canReturnDebugCode() {
        // 只有“关闭真实邮箱 + 显式打开调试码”时，接口才会把验证码返回给前端。
        // 线上必须保持 false，否则任何人都能直接拿到验证码。
        return !mailEnabled && debugCodeEnabled;
    }

    public void sendVerificationCode(String to, String code) {
        // 发送前先检查开关和必要参数，失败时抛 BusinessException，让全局异常处理器返回友好提示。
        if (!mailEnabled) {
            if (debugCodeEnabled) {
                log.info("Mail delivery disabled; exposing debug verification code for {}", to);
                return;
            }
            throw new BusinessException(503, "邮箱发送服务未启用，请配置 SMTP 后再发送验证码");
        }

        if (!StringUtils.hasText(from)) {
            throw new BusinessException(503, "邮箱发件人未配置，请设置 MAIL_USERNAME 或 MAIL_FROM");
        }

        if (!StringUtils.hasText(to)) {
            throw new BusinessException("邮箱不能为空");
        }

        if (!StringUtils.hasText(code)) {
            throw new BusinessException("验证码不能为空");
        }

        if (!canSendMail()) {
            throw new BusinessException(503, "邮箱发送服务配置不完整");
        }

        String normalizedTo = to.trim();
        if (!StringUtils.hasText(normalizedTo)) {
            throw new BusinessException("邮箱不能为空");
        }

        int expireMinutes = Math.max(1, verificationCodeExpireSeconds / 60);

        // SimpleMailMessage 是 Spring 提供的普通文本邮件对象，适合验证码这种纯文本内容。
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(normalizedTo);
        message.setSubject("邮箱验证码");
        message.setText("您的验证码是 " + code + "，将在 " + expireMinutes + " 分钟后失效。");

        try {
            // 真正调用 SMTP 服务器。如果 QQ 邮箱授权码错误、网络不通，会在这里抛异常。
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send verification mail", ex);
            throw new BusinessException(502, "验证码邮件发送失败，请稍后重试");
        }
    }
}
