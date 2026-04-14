package com.example.刷题.aspect;

import com.example.刷题.annotation.CacheUpdate;
import com.example.刷题.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 缓存更新AOP切面
 * 实现缓存更新时删除缓存的逻辑
 */
@Slf4j
@Aspect
@Component
public class CacheUpdateAspect {

    @Autowired
    private CacheService cacheService;

    /**
     * 定义切点：标记了@CacheUpdate注解的方法
     */
    @Pointcut("@annotation(com.example.刷题.annotation.CacheUpdate)")
    public void cacheUpdatePointcut() {}

    /**
     * 后置通知：方法执行成功后删除缓存
     */
    @AfterReturning("cacheUpdatePointcut()")
    public void afterCacheUpdate(JoinPoint joinPoint) {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 获取@CacheUpdate注解
        CacheUpdate cacheUpdate = method.getAnnotation(CacheUpdate.class);
        if (cacheUpdate == null) {
            return;
        }

        // 获取缓存键前缀
        String keyPrefix = cacheUpdate.keyPrefix();
        
        // 获取方法参数
        Object[] args = joinPoint.getArgs();
        
        // 获取缓存键参数索引
        int keyParamIndex = cacheUpdate.keyParamIndex();
        
        // 检查参数索引是否有效
        if (keyParamIndex >= 0 && keyParamIndex< args.length) {
            Object keyParam = args[keyParamIndex];
            if (keyParam != null) {
                // 构建完整的缓存键
                String cacheKey = keyPrefix + ":" + keyParam.toString();
                // 删除缓存
                cacheService.delete(cacheKey);
                log.info("方法{}执行成功，删除缓存：{}", method.getName(), cacheKey);
            }
        } else {
            // 如果没有指定参数索引或参数不存在，删除所有以该前缀开头的缓存
            log.info("方法{}执行成功，删除所有以{}开头的缓存", method.getName(), keyPrefix);
            // 这里可以扩展实现批量删除逻辑
        }
    }
}
