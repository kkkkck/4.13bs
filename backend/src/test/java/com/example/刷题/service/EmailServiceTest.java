package com.example.刷题.service;

import com.example.刷题.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService();
        ReflectionTestUtils.setField(emailService, "mailSender", mailSender);
        ReflectionTestUtils.setField(emailService, "from", "noreply@example.com");
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "debugCodeEnabled", false);
        ReflectionTestUtils.setField(emailService, "verificationCodeExpireSeconds", 300);
        ReflectionTestUtils.setField(emailService, "sendRetryAttempts", 2);
        ReflectionTestUtils.setField(emailService, "retryBackoffMillis", 0L);
    }

    @Test
    void sendVerificationCodeSendsMailWhenSmtpEnabled() {
        emailService.sendVerificationCode("user@example.com", "123456");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertEquals("noreply@example.com", message.getFrom());
        assertEquals("user@example.com", message.getTo()[0]);
        assertEquals("邮箱验证码", message.getSubject());
        assertTrue(message.getText().contains("123456"));
        assertTrue(message.getText().contains("5 分钟"));
    }

    @Test
    void sendVerificationCodeRetriesTransientSenderFailure() {
        doThrow(new MailSendException("timeout"))
                .doNothing()
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        emailService.sendVerificationCode("user@example.com", "123456");

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendVerificationCodeRejectsDisabledMailWithoutDebugMode() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> emailService.sendVerificationCode("user@example.com", "123456")
        );

        assertEquals(503, exception.getCode());
        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    void sendVerificationCodeSkipsSenderWhenDebugModeEnabled() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);
        ReflectionTestUtils.setField(emailService, "debugCodeEnabled", true);

        emailService.sendVerificationCode("user@example.com", "123456");

        assertTrue(emailService.canReturnDebugCode());
        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }
}
