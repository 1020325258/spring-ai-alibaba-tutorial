package com.yycome.sremate.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存服务
 * 基于Caffeine实现高性能缓存
 */
@Slf4j
@Service
public class CacheService {

    private final Cache<String, CacheEntry> cache;

    public CacheService() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
                .build();

        log.info("[CACHE] 缓存服务初始化完成");
    }

    /**
     * 获取或计算缓存值
     *
     * @param key 缓存键
     * @param supplier 值提供者
     * @param ttl 过期时间
     * @return 缓存值
     */
    public <T> T getOrCompute(String key, Supplier<T> supplier, Duration ttl) {
        CacheEntry entry = cache.get(key, k -> {
            T value = supplier.get();
            log.debug("[CACHE] 计算新值: key={}, ttl={}", key, ttl);
            return new CacheEntry(value, ttl);
        });

        if (entry != null) {
            log.debug("[CACHE] 命中缓存: key={}", key);
            return (T) entry.getValue();
        }

        return null;
    }

    /**
     * 获取或计算缓存值（使用默认过期时间）
     *
     * @param key 缓存键
     * @param supplier 值提供者
     * @return 缓存值
     */
    public <T> T getOrCompute(String key, Supplier<T> supplier) {
        return getOrCompute(key, supplier, Duration.ofMinutes(10));
    }

    /**
     * 手动放入缓存
     *
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 过期时间
     */
    public <T> void put(String key, T value, Duration ttl) {
        cache.put(key, new CacheEntry(value, ttl));
        log.debug("[CACHE] 放入缓存: key={}, ttl={}", key, ttl);
    }

    /**
     * 手动放入缓存（使用默认过期时间）
     *
     * @param key 缓存键
     * @param value 缓存值
     */
    public <T> void put(String key, T value) {
        put(key, value, Duration.ofMinutes(10));
    }

    /**
     * 获取缓存值
     *
     * @param key 缓存键
     * @return 缓存值，不存在则返回null
     */
    public <T> T get(String key) {
        CacheEntry entry = cache.getIfPresent(key);
        if (entry != null) {
            log.debug("[CACHE] 获取缓存: key={}", key);
            return (T) entry.getValue();
        }
        return null;
    }

    /**
     * 移除缓存值
     *
     * @param key 缓存键
     */
    public void evict(String key) {
        cache.invalidate(key);
        log.debug("[CACHE] 移除缓存: key={}", key);
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        cache.invalidateAll();
        log.info("[CACHE] 清空所有缓存");
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计
     */
    public CacheStats getStats() {
        return cache.stats();
    }

    /**
     * 获取缓存统计摘要
     *
     * @return 统计摘要字符串
     */
    public String getStatsSummary() {
        CacheStats stats = getStats();
        StringBuilder sb = new StringBuilder();
        sb.append("=== 缓存统计 ===\n");
        sb.append(String.format("请求数: %d%n", stats.requestCount()));
        sb.append(String.format("命中数: %d%n", stats.hitCount()));
        sb.append(String.format("未命中数: %d%n", stats.missCount()));
        sb.append(String.format("命中率: %.2f%%%n", stats.hitRate() * 100));
        sb.append(String.format("平均加载时间: %.2fms%n", stats.averageLoadPenalty() / 1_000_000));
        sb.append(String.format("缓存大小: %d%n", cache.estimatedSize()));

        return sb.toString();
    }

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        private final Object value;
        private final long expireTime;

        public CacheEntry(Object value, Duration ttl) {
            this.value = value;
            this.expireTime = System.currentTimeMillis() + ttl.toMillis();
        }

        public Object getValue() {
            return value;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}
