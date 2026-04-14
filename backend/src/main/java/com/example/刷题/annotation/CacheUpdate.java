package com.example.刷题.annotation;

import java.lang.annotation.*;

/**
 * 缓存更新注解
 * 标记在需要更新缓存的方法上
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheUpdate {

    /**
     * 缓存键前缀
     */
    String keyPrefix();

    /**
     * 缓存键参数索引（用于从方法参数中提取缓存键）
     */
    int keyParamIndex() default 0;
}
