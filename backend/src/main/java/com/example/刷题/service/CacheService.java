package com.example.刷题.service;

/**
 * 三级缓存服务接口
 * 实现L1（Caffeine本地缓存）、L2（Redis分布式缓存）、L3（MySQL数据库）的三级缓存架构
 */
public interface CacheService {

    /**
     * 获取缓存数据
     * @param key 缓存键
     * @param clazz 返回值类型
     * @param dbLoader 数据库加载函数
     * @param <T> 返回值泛型
     * @return 缓存数据
     */
    <T> T get(String key, Class<T> clazz, DbLoader<T> dbLoader);

    /**
     * 删除缓存
     * @param key 缓存键
     */
    void delete(String key);

    /**
     * 按前缀批量删除缓存
     * @param keyPrefix 缓存键前缀
     */
    void deleteByPrefix(String keyPrefix);

    /**
     * 数据库加载函数接口
     */
    @FunctionalInterface
    interface DbLoader<T> {
        /**
         * 从数据库加载数据
         * @return 数据
         */
        T load();
    }
}
