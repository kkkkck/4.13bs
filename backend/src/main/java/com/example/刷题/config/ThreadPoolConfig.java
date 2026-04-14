package com.example.刷题.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置类
 * 用于异步回填L1和L2缓存
 */
@Configuration
public class ThreadPoolConfig {

    /**
     * 创建缓存更新线程池
     * 核心线程数：5
     * 最大线程数：10
     * 队列容量：100
     */
    @Bean(name = "cacheThreadPool")
    public ThreadPoolTaskExecutor cacheThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(5);
        // 最大线程数
        executor.setMaxPoolSize(10);
        // 队列容量
        executor.setQueueCapacity(100);
        // 线程名称前缀
        executor.setThreadNamePrefix("cache-pool-");
        // 线程空闲时间
        executor.setKeepAliveSeconds(60);
        // 拒绝策略：丢弃并抛出异常
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 初始化
        executor.initialize();
        return executor;
    }
}
