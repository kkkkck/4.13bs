package com.example.刷题.service;

import com.example.刷题.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class VerificationCodeService {

    private final Map<String, VerificationCode> codeMap = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldownMap = new ConcurrentHashMap<>();
    private final Random random = new Random();

    @Value("${app.auth.verification-code-expire-seconds:300}")
    private long codeExpireSeconds;

    @Value("${app.auth.verification-code-cooldown-seconds:60}")
    private long cooldownSeconds;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    public String generateCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new BusinessException("邮箱不能为空");
        }

        if (stringRedisTemplate != null) {
            try {
                Boolean limited = stringRedisTemplate.hasKey("auth:limit:" + normalizedEmail);
                if (Boolean.TRUE.equals(limited)) {
                    throw new IllegalStateException("请等待 60 秒后再重新获取验证码");
                }
            } catch (IllegalStateException ex) {
                throw ex;
            } catch (Exception ex) {
                log.warn("Redis unavailable for verification limit", ex);
            }
        }

        Long expireAt = cooldownMap.get(normalizedEmail);
        if (expireAt != null && expireAt > System.currentTimeMillis()) {
            throw new IllegalStateException("请等待 60 秒后再重新获取验证码");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        long now = System.currentTimeMillis();
        cooldownMap.put(normalizedEmail, now + cooldownSeconds * 1000L);
        codeMap.put(normalizedEmail, new VerificationCode(code, now));

        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.opsForValue().set("auth:code:" + normalizedEmail, code, Duration.ofSeconds(codeExpireSeconds));
                stringRedisTemplate.opsForValue().set("auth:limit:" + normalizedEmail, "1", Duration.ofSeconds(cooldownSeconds));
            } catch (Exception ex) {
                log.warn("Redis unavailable for verification code", ex);
            }
        }

        log.info("Generated verification code {} for {}", code, normalizedEmail);
        return code;
    }

    public boolean verifyCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = code == null ? "" : code.trim();

        if (!StringUtils.hasText(normalizedEmail) || !StringUtils.hasText(normalizedCode)) {
            return false;
        }

        if (stringRedisTemplate != null) {
            try {
                String cached = stringRedisTemplate.opsForValue().get("auth:code:" + normalizedEmail);
                if (cached != null) {
                    boolean matched = cached.equals(normalizedCode);
                    if (matched) {
                        stringRedisTemplate.delete("auth:code:" + normalizedEmail);
                    }
                    return matched;
                }
            } catch (Exception ex) {
                log.warn("Redis unavailable when verifying code", ex);
            }
        }

        VerificationCode verificationCode = codeMap.get(normalizedEmail);
        if (verificationCode == null) {
            return false;
        }
        if (!verificationCode.code().equals(normalizedCode)) {
            return false;
        }
        if (System.currentTimeMillis() - verificationCode.createTime() > codeExpireSeconds * 1000L) {
            codeMap.remove(normalizedEmail);
            return false;
        }

        codeMap.remove(normalizedEmail);
        return true;
    }

    public int getCodeExpireSeconds() {
        return Math.toIntExact(codeExpireSeconds);
    }

    public int getCooldownSeconds() {
        return Math.toIntExact(cooldownSeconds);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record VerificationCode(String code, long createTime) {
    }
}
