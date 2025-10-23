package com.wait.util.localcache;

import com.google.common.cache.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 功能完备的 Guava 缓存工具类
 * 包含多种缓存策略、统计、监听、预热等功能
 */
@Component
@Slf4j
public class GuavaCacheUtil {

    // ==================== 多种缓存策略配置 ====================

    /* 1. 基础缓存 - 最常见的配置 */
    private Cache<String, Object> basicCache = CacheBuilder.newBuilder()
            .maximumSize(1000)                         // 最大容量
            .expireAfterWrite(10, TimeUnit.MINUTES)    // 写入后过期时间
            .expireAfterAccess(5, TimeUnit.MINUTES)    // 访问后过期时间
            .concurrencyLevel(8)                       // 并发级别
            .recordStats()                             // 开启统计
            .build();

    /* 2. 自动加载缓存 - 支持缓存未命中时自动加载数据 */
    private LoadingCache<String, Object> loadingCache = CacheBuilder.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .refreshAfterWrite(2, TimeUnit.MINUTES)    // 定时刷新（不阻塞读）
            .build(new CacheLoader<String, Object>() {
                @Override
                public Object load(String key) throws Exception {
                    log.info("自动加载数据: {}", key);
                    return loadFromDataSource(key);
                }

                @Override
                public Map<String, Object> loadAll(Iterable<? extends String> keys) throws Exception {
                    log.info("批量加载数据: {}", keys);
                    return batchLoadFromDataSource(keys);
                }
            });

    /* 3. 各种类型的缓存 */
    private Cache<String, Object> typeRefCache = CacheBuilder.newBuilder()
            .maximumSize(500)
//            .softValues()                              // 内存不足时GC回收。软引用缓存 - 内存敏感型缓存
//            .weakValues()                              // GC时立即回收。弱引用缓存 - 临时性缓存
            .expireAfterWrite(1, TimeUnit.HOURS) // 定时失效缓存 - 短期缓存。1h过期
            .removalListener(createRemovalListener())  // 移除监听
            .build();

    // ==================== 缓存统计监控 ====================
    private Map<String, CacheStats> statsHistory = new ConcurrentHashMap<>();

    /**
     * 创建移除监听器
     */
    private RemovalListener<String, Object> createRemovalListener() {
        return notification -> {
            String key = notification.getKey();
            RemovalCause cause = notification.getCause();
            log.info("缓存移除通知 - Key: {}, Cause: {}, WasEvicted: {}",
                    key, cause, cause.wasEvicted());

            // 根据移除原因进行不同处理
            switch (cause) {
                case EXPLICIT:
                    log.debug("缓存被显式删除: {}", key);
                    break;
                case REPLACED:
                    log.debug("缓存被替换: {}", key);
                    break;
                case EXPIRED:
                    log.debug("缓存过期: {}", key);
                    break;
                case SIZE:
                    log.warn("缓存因大小限制被移除: {}", key);
                    break;
                case COLLECTED:
                    log.info("缓存被GC回收: {}", key);
                    break;
            }
        };
    }

    // ==================== 核心操作方法 ====================

    /**
     * 基础获取操作（自动加载）
     */
    public Object get(String key) {
        try {
            return loadingCache.get(key);
        } catch (ExecutionException e) {
            log.error("缓存加载失败, key: {}", key, e);
            return null;
        }
    }

    /**
     * 带默认值的获取操作
     */
    public Object getOrDefault(String key, Object defaultValue) {
        try {
            Object value = loadingCache.get(key);
            return value != null ? value : defaultValue;
        } catch (ExecutionException e) {
            log.warn("缓存加载失败，返回默认值, key: {}", key, e);
            return defaultValue;
        }
    }

    /**
     * 批量获取
     */
    public Map<String, Object> getAll(Iterable<String> keys) {
        try {
            return loadingCache.getAll(keys);
        } catch (ExecutionException e) {
            log.error("批量缓存加载失败", e);
            return java.util.Collections.emptyMap();
        }
    }

    /**
     * 手动放入缓存
     */
    public void put(String key, Object value) {
        basicCache.put(key, value);
        loadingCache.put(key, value); // 同时更新多个缓存
    }

    /**
     * 批量放入
     */
    public void putAll(Map<String, Object> keyValues) {
        basicCache.putAll(keyValues);
        loadingCache.putAll(keyValues);
    }

    /**
     * 删除缓存
     */
    public void invalidate(String key) {
        basicCache.invalidate(key);
        loadingCache.invalidate(key);
        softRefCache.invalidate(key);
    }

    /**
     * 批量删除
     */
    public void invalidateAll(Iterable<String> keys) {
        basicCache.invalidateAll(keys);
        loadingCache.invalidateAll(keys);
        softRefCache.invalidateAll(keys);
    }

    /**
     * 清空所有缓存
     */
    public void invalidateAll() {
        basicCache.invalidateAll();
        loadingCache.invalidateAll();
        softRefCache.invalidateAll();
        weakRefCache.invalidateAll();
        shortTermCache.invalidateAll();
    }

    /**
     * 获取缓存统计信息
     */
    public void printCacheStats() {
        CacheStats basicStats = basicCache.stats();
        CacheStats loadingStats = loadingCache.stats();

        log.info("=== 缓存统计信息 ===");
        log.info("基础缓存 - 命中率: {:.2f}%, 命中数: {}, 未命中数: {}, 加载次数: {}",
                basicStats.hitRate() * 100, basicStats.hitCount(),
                basicStats.missCount(), basicStats.loadCount());

        log.info("加载缓存 - 命中率: {:.2f}%, 加载成功: {}, 加载异常: {}, 平均加载时间: {}ms",
                loadingStats.hitRate() * 100, loadingStats.loadSuccessCount(),
                loadingStats.loadExceptionCount(),
                loadingStats.averageLoadPenalty() / 1000000);

        // 记录历史统计
        statsHistory.put(String.valueOf(System.currentTimeMillis()), basicStats);
    }

    /**
     * 手动刷新缓存
     */
    public void refresh(String key) {
        if (loadingCache instanceof LoadingCache) {
            ((LoadingCache<String, Object>) loadingCache).refresh(key);
        }
    }

    /**
     * 检查缓存是否存在
     */
    public boolean containsKey(String key) {
        return basicCache.getIfPresent(key) != null ||
                loadingCache.getIfPresent(key) != null;
    }

    /**
     * 获取缓存大小
     */
    public long size() {
        return basicCache.size() + loadingCache.size();
    }

    /**
     * 获取所有缓存键（注意：生产环境慎用，性能开销大）
     */
    public Iterable<String> getAllKeys() {
        // Guava Cache 不直接支持获取所有键，需要结合业务实现
        log.warn("获取所有缓存键操作性能开销较大，请谨慎使用");
        return java.util.Collections.emptyList();
    }

    // ==================== 高级功能 ====================

    /**
     * 缓存预热
     */
    public void warmUpCache() {
        String[] hotKeys = {"system_config", "user_roles", "product_categories", "hot_news"};

        log.info("开始缓存预热...");
        try {
            loadingCache.getAll(Arrays.asList(hotKeys));
            log.info("缓存预热完成，预热 {} 个键", hotKeys.length);
        } catch (Exception e) {
            log.warn("缓存预热失败", e);
        }
    }

    /**
     * 根据模式删除缓存（模拟功能）
     */
    public void invalidateByPattern(String pattern) {
        // 实际项目中可能需要结合Redis的keys或scan命令
        // 这里只是模拟实现
        log.info("根据模式删除缓存: {}", pattern);
        // 实现逻辑...
    }

    /**
     * 获取缓存信息快照
     */
    public CacheInfo getCacheInfo() {
        return CacheInfo.builder()
                .basicCacheSize(basicCache.size())
                .loadingCacheSize(loadingCache.size())
                .softRefCacheSize(softRefCache.size())
                .totalSize(size())
                .build();
    }

    // ==================== 数据源加载方法 ====================

    /**
     * 模拟从数据源加载数据
     */
    private Object loadFromDataSource(String key) {
        // 模拟加载延迟
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 实际项目中这里可能是数据库查询、API调用等
        return "data_for_" + key + "_at_" + System.currentTimeMillis();
    }

    /**
     * 模拟批量加载数据
     */
    private Map<String, Object> batchLoadFromDataSource(Iterable<? extends String> keys) {
        Map<String, Object> result = new java.util.HashMap<>();
        for (String key : keys) {
            result.put(key, loadFromDataSource(key));
        }
        return result;
    }

    // ==================== 缓存信息对象 ====================

    @lombok.Builder
    @lombok.Data
    public static class CacheInfo {
        private long basicCacheSize;
        private long loadingCacheSize;
        private long softRefCacheSize;
        private long totalSize;
    }

}