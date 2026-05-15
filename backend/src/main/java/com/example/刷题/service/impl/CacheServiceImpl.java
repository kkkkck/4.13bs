package com.example.刷题.service.impl;

import com.example.刷题.service.CacheService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 三级缓存服务实现类
 * L1: Caffeine本地缓存（纳秒级）
 * L2: Redis分布式缓存（微秒级）
 * L3: MySQL数据库（毫秒级）
 */
@Slf4j
@Service
public class CacheServiceImpl implements CacheService {

    // Caffeine本地缓存
    private final Cache<String, Object> caffeineCache;

    // Redis分布式缓存
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 线程池，用于异步回填缓存
    @Autowired
    private ThreadPoolTaskExecutor cacheThreadPool;

    // 空值缓存标记
    private static final Object NULL_VALUE = new Object();

    public CacheServiceImpl() {
        // 配置Caffeine本地缓存
        this.caffeineCache = Caffeine.newBuilder()
                // 最大容量10000条
                .maximumSize(10000)
                // 写入后30分钟过期
                .expireAfterWrite(30, TimeUnit.MINUTES)
                // 访问后10分钟刷新
                .expireAfterAccess(10, TimeUnit.MINUTES)
                // 记录缓存统计信息
                .recordStats()
                .build();
    }

    @Override
    public <T> T get(String key, Class<T> clazz, DbLoader<T> dbLoader) {
        // 读缓存的顺序：先本机内存 L1，再 Redis L2，最后才查 MySQL L3。
        // 这样热点题目不会每次都打到数据库，答辩时可以把它作为性能优化点说明。
        log.info("获取缓存数据，key: {}", key);
        
        // L1: 查询Caffeine本地缓存
        Object result = caffeineCache.getIfPresent(key);
        if (result != null) {
            log.info("L1缓存命中，key: {}", key);
            if (result == NULL_VALUE) {
                return null; // 空值缓存
            }
            return clazz.cast(result);
        }

        // L2: 查询Redis分布式缓存
        try {
            result = redisTemplate.opsForValue().get(key);
            if (result != null) {
                log.info("L2缓存命中，key: {}", key);
                if (result == NULL_VALUE) {
                    return null; // 空值缓存
                }
                // 异步回填L1缓存
                final String finalKey = key;
                final Object finalResult = result;
                cacheThreadPool.execute(() -> {
                    caffeineCache.put(finalKey, finalResult);
                    log.info("异步回填L1缓存，key: {}", finalKey);
                });
                return clazz.cast(result);
            }
        } catch (Exception e) {
            log.warn("Redis连接失败，降级到L1和L3缓存，key: {}, error: {}", key, e.getMessage());
        }

        // L3: 查询MySQL数据库
        log.info("L1和L2缓存未命中，查询数据库，key: {}", key);
        T data = dbLoader.load();

        // 异步回填缓存
        // 数据库结果异步写回 L1/L2，不阻塞当前请求返回。
        final String finalKey2 = key;
        final T finalData = data;
        cacheThreadPool.execute(() -> {
            try {
                if (finalData == null) {
                    // 缓存穿透防护：存储空值缓存
                    caffeineCache.put(finalKey2, NULL_VALUE);
                    try {
                        redisTemplate.opsForValue().set(finalKey2, NULL_VALUE, Duration.ofMinutes(5));
                    } catch (Exception e) {
                        log.warn("Redis连接失败，跳过L2缓存回填，key: {}, error: {}", finalKey2, e.getMessage());
                    }
                    log.info("缓存空值，防止缓存穿透，key: {}", finalKey2);
                } else {
                    // 回填L1和L2缓存
                    caffeineCache.put(finalKey2, finalData);
                    try {
                        redisTemplate.opsForValue().set(finalKey2, finalData, Duration.ofHours(1));
                    } catch (Exception e) {
                        log.warn("Redis连接失败，跳过L2缓存回填，key: {}, error: {}", finalKey2, e.getMessage());
                    }
                    log.info("异步回填L1和L2缓存，key: {}", finalKey2);
                }
            } catch (Exception e) {
                log.error("异步回填缓存失败，key: {}, error: {}", finalKey2, e.getMessage());
            }
        });

        return data;
    }

    @Override
    public void delete(String key) {
        log.info("删除缓存，key: {}", key);
        // 删除L1缓存
        caffeineCache.invalidate(key);
        // 删除L2缓存
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis delete skipped, key: {}, error: {}", key, e.getMessage());
        }
        log.info("缓存删除成功，key: {}", key);
    }

    @Override
    public void deleteByPrefix(String keyPrefix) {
        // 修改题目后，分类列表缓存会失效；按前缀批量删除可以保证用户看到的是最新题目。
        log.info("按前缀删除缓存，prefix: {}", keyPrefix);

        caffeineCache.asMap().keySet().removeIf(key -> key.startsWith(keyPrefix));

        try {
            Set<String> keys = redisTemplate.keys(keyPrefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis deleteByPrefix skipped, prefix: {}, error: {}", keyPrefix, e.getMessage());
        }
    }
}
