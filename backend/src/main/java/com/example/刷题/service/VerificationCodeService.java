package com.example.刷题.service;

import com.example.刷题.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class VerificationCodeService {
    private static final String CODE_KEY_PREFIX = "auth:code:";
    private static final String LIMIT_KEY_PREFIX = "auth:limit:";
    private static final DefaultRedisScript<Long> VERIFY_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final Map<String, VerificationCode> codeMap = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldownMap = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

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

        reserveCooldown(normalizedEmail);

        String code = String.format("%06d", random.nextInt(1_000_000));
        long now = System.currentTimeMillis();
        codeMap.put(normalizedEmail, new VerificationCode(code, now));

        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.opsForValue().set(codeKey(normalizedEmail), code, Duration.ofSeconds(codeExpireSeconds));
            } catch (Exception ex) {
                log.warn("Redis unavailable for verification code, error: {}", ex.getMessage());
            }
        }

        log.info("Generated verification code for {}", normalizedEmail);
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
                Long deleted = stringRedisTemplate.execute(
                        VERIFY_AND_DELETE_SCRIPT,
                        Collections.singletonList(codeKey(normalizedEmail)),
                        normalizedCode
                );
                if (deleted != null) {
                    boolean matched = deleted > 0;
                    if (matched) {
                        codeMap.remove(normalizedEmail);
                    }
                    return matched;
                }
            } catch (Exception ex) {
                log.warn("Redis unavailable when verifying code, error: {}", ex.getMessage());
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
            codeMap.remove(normalizedEmail, verificationCode);
            return false;
        }

        return codeMap.remove(normalizedEmail, verificationCode);
    }

    public void invalidateCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return;
        }

        codeMap.remove(normalizedEmail);
        cooldownMap.remove(normalizedEmail);

        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.delete(codeKey(normalizedEmail));
                stringRedisTemplate.delete(limitKey(normalizedEmail));
            } catch (Exception ex) {
                log.warn("Redis unavailable when invalidating verification code, error: {}", ex.getMessage());
            }
        }
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

    private void reserveCooldown(String normalizedEmail) {
        if (cooldownSeconds <= 0) {
            return;
        }

        if (stringRedisTemplate != null) {
            try {
                Boolean reserved = stringRedisTemplate.opsForValue().setIfAbsent(
                        limitKey(normalizedEmail),
                        "1",
                        Duration.ofSeconds(cooldownSeconds)
                );
                if (Boolean.FALSE.equals(reserved)) {
                    throw new IllegalStateException(rateLimitMessage());
                }
                if (Boolean.TRUE.equals(reserved)) {
                    cooldownMap.put(normalizedEmail, System.currentTimeMillis() + cooldownSeconds * 1000L);
                    return;
                }
            } catch (IllegalStateException ex) {
                throw ex;
            } catch (Exception ex) {
                log.warn("Redis unavailable for verification limit, error: {}", ex.getMessage());
            }
        }

        long now = System.currentTimeMillis();
        long nextAllowedAt = now + cooldownSeconds * 1000L;
        AtomicBoolean limited = new AtomicBoolean(false);
        cooldownMap.compute(normalizedEmail, (email, currentNextAllowedAt) -> {
            if (currentNextAllowedAt != null && currentNextAllowedAt > now) {
                limited.set(true);
                return currentNextAllowedAt;
            }
            return nextAllowedAt;
        });

        if (limited.get()) {
            throw new IllegalStateException(rateLimitMessage());
        }
    }

    private String rateLimitMessage() {
        return "请等待 " + cooldownSeconds + " 秒后再重新获取验证码";
    }

    private String codeKey(String normalizedEmail) {
        return CODE_KEY_PREFIX + normalizedEmail;
    }

    private String limitKey(String normalizedEmail) {
        return LIMIT_KEY_PREFIX + normalizedEmail;
    }

    private record VerificationCode(String code, long createTime) {
    }
}
