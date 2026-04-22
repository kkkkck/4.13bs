package com.example.刷题.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationCodeServiceTest {

    private VerificationCodeService verificationCodeService;

    @BeforeEach
    void setUp() {
        verificationCodeService = new VerificationCodeService();
        ReflectionTestUtils.setField(verificationCodeService, "codeExpireSeconds", 300L);
        ReflectionTestUtils.setField(verificationCodeService, "cooldownSeconds", 60L);
        ReflectionTestUtils.setField(verificationCodeService, "stringRedisTemplate", null);
    }

    @Test
    void generateCodeAllowsOnlyOneConcurrentRequestPerEmailDuringCooldown() throws Exception {
        int workers = 24;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < workers; i += 1) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    verificationCodeService.generateCode("User@Example.com");
                    return true;
                } catch (IllegalStateException ignored) {
                    return false;
                }
            }));
        }

        ready.await();
        start.countDown();

        int successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount += 1;
            }
        }
        executor.shutdownNow();

        assertEquals(1, successCount);
    }

    @Test
    void verifyCodeConsumesLocalCodeOnlyOnceUnderConcurrency() throws Exception {
        String code = verificationCodeService.generateCode("User@Example.com");
        int workers = 24;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < workers; i += 1) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return verificationCodeService.verifyCode("user@example.com", code);
            }));
        }

        ready.await();
        start.countDown();

        int successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount += 1;
            }
        }
        executor.shutdownNow();

        assertEquals(1, successCount);
        assertFalse(verificationCodeService.verifyCode("user@example.com", code));
    }

    @Test
    void invalidateCodeRemovesGeneratedCodeAndCooldown() {
        String code = verificationCodeService.generateCode("user@example.com");

        verificationCodeService.invalidateCode("user@example.com");

        assertFalse(verificationCodeService.verifyCode("user@example.com", code));
        assertTrue(verificationCodeService.generateCode("user@example.com").matches("\\d{6}"));
    }
}
