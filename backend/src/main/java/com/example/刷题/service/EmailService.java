package com.example.刷题.service;

import com.example.刷题.exception.BusinessException;
import jakarta.mail.AuthenticationFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class EmailService {
    private static final int DEFAULT_SMTP_PORT = 25;
    private static final int MAX_SMTP_TARGETS = 6;
    private final AtomicReference<String> preferredSmtpTarget = new AtomicReference<>();

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

    @Value("${app.mail.send-retry-attempts:2}")
    private int sendRetryAttempts;

    @Value("${app.mail.retry-backoff-millis:800}")
    private long retryBackoffMillis;

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
            sendWithRetry(message);
        } catch (Exception ex) {
            log.error("Failed to send verification mail", ex);
            throw new BusinessException(502, "验证码邮件发送失败，请稍后重试");
        }
    }

    private void sendWithRetry(SimpleMailMessage message) {
        int attempts = Math.max(1, sendRetryAttempts);
        Exception lastException = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            List<MailSenderTarget> targets = resolveMailSenderTargets();
            for (MailSenderTarget target : targets) {
                try {
                    target.sender().send(message);
                    preferredSmtpTarget.set(target.host());
                    if (attempt > 1 || !target.primary()) {
                        log.info("Verification mail sent after SMTP retry, attempt={}, smtpTarget={}", attempt, target.label());
                    }
                    return;
                } catch (Exception ex) {
                    lastException = ex;
                    log.warn(
                            "Verification mail send attempt failed, attempt={}/{}, smtpTarget={}, error={}",
                            attempt,
                            attempts,
                            target.label(),
                            rootCauseMessage(ex)
                    );

                    if (isAuthenticationFailure(ex)) {
                        if (ex instanceof RuntimeException runtimeException) {
                            throw runtimeException;
                        }
                        throw new IllegalStateException("SMTP authentication failed", ex);
                    }
                }
            }

            sleepBeforeRetry(attempt, attempts);
        }

        if (lastException instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException("SMTP send failed", lastException);
    }

    private List<MailSenderTarget> resolveMailSenderTargets() {
        if (!(mailSender instanceof JavaMailSenderImpl senderImpl)) {
            return List.of(new MailSenderTarget(mailSender, "configured-smtp", 0, true));
        }

        String configuredHost = senderImpl.getHost();
        if (!StringUtils.hasText(configuredHost)) {
            return List.of(new MailSenderTarget(mailSender, "configured-smtp", 0, true));
        }

        String host = configuredHost.trim();
        int port = senderImpl.getPort() > 0 ? senderImpl.getPort() : DEFAULT_SMTP_PORT;
        LinkedHashSet<String> smtpTargets = new LinkedHashSet<>();
        String preferredTarget = preferredSmtpTarget.get();
        if (StringUtils.hasText(preferredTarget)) {
            smtpTargets.add(preferredTarget);
        }
        smtpTargets.add(host);

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                smtpTargets.add(address.getHostAddress());
            }
        } catch (UnknownHostException ex) {
            log.warn("Failed to resolve SMTP host {}, error={}", host, ex.getMessage());
        }

        List<MailSenderTarget> targets = new ArrayList<>();
        for (String smtpTarget : smtpTargets) {
            if (targets.size() >= MAX_SMTP_TARGETS) {
                break;
            }

            boolean primary = smtpTarget.equals(host);
            JavaMailSender targetSender = primary ? mailSender : copyMailSender(senderImpl, smtpTarget, port, true);
            targets.add(new MailSenderTarget(targetSender, smtpTarget, port, primary));
        }

        return targets;
    }

    private JavaMailSenderImpl copyMailSender(JavaMailSenderImpl source, String host, int port, boolean directResolvedAddress) {
        JavaMailSenderImpl copied = new JavaMailSenderImpl();
        copied.setHost(host);
        copied.setPort(port);
        copied.setUsername(source.getUsername());
        copied.setPassword(source.getPassword());

        if (StringUtils.hasText(source.getProtocol())) {
            copied.setProtocol(source.getProtocol());
        }
        if (StringUtils.hasText(source.getDefaultEncoding())) {
            copied.setDefaultEncoding(source.getDefaultEncoding());
        }

        Properties properties = new Properties();
        properties.putAll(source.getJavaMailProperties());
        if (directResolvedAddress) {
            // These fallback targets are IPs resolved from the configured SMTP host. QQ's TLS
            // certificate is issued for smtp.qq.com, so raw-IP fallback cannot pass hostname
            // identity checks even though it reaches the same server.
            properties.put("mail.smtp.ssl.checkserveridentity", "false");
            properties.put("mail.smtps.ssl.checkserveridentity", "false");
        }
        copied.setJavaMailProperties(properties);
        return copied;
    }

    private void sleepBeforeRetry(int attempt, int attempts) {
        if (attempt >= attempts) {
            return;
        }

        long backoffMillis = Math.max(0, retryBackoffMillis);
        if (backoffMillis == 0) {
            return;
        }

        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying SMTP send", ex);
        }
    }

    private boolean isAuthenticationFailure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof AuthenticationFailedException || current instanceof MailAuthenticationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String rootCauseMessage(Throwable ex) {
        Throwable rootCause = ex;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        String message = rootCause.getMessage();
        if (!StringUtils.hasText(message)) {
            return rootCause.getClass().getSimpleName();
        }
        return rootCause.getClass().getSimpleName() + ": " + message;
    }

    private record MailSenderTarget(JavaMailSender sender, String host, int port, boolean primary) {
        private String label() {
            if (port <= 0) {
                return host;
            }
            return host + ":" + port;
        }
    }
}
