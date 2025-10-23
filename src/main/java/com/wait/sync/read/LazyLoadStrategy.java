package com.wait.sync.read;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.ReadStrategyType;
import com.wait.util.AsyncSQLWrapper;
import com.wait.util.BoundUtil;
import com.wait.util.lock.Lock;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LazyLoadStrategy implements ReadStrategy {

    @Autowired
    private BoundUtil boundUtil;

    @Autowired
    private Lock lock;
    
    @Autowired
    private AsyncSQLWrapper asyncSQLWrapper;

    @Override
    public <T> T read(CacheSyncParam<T> param, ProceedingJoinPoint joinPoint) {
        // 1. 先查缓存
        T cachedValue = boundUtil.getFromCache(param);
        if (cachedValue != null) {
            log.debug("懒加载缓存命中: {}", param.getKey());
            return cachedValue;
        }

        // 2. 缓存未命中，加锁加载
        return loadWithLock(param, joinPoint);
    }

    private <T> T loadWithLock(CacheSyncParam<T> param, ProceedingJoinPoint joinPoint) {

        String key = param.getKey();
        try {
            if (lock.getLock(key)) {
                try {
                    // 双重检查
                    T doubleCheckValue = boundUtil.getFromCache(param);
                    if (doubleCheckValue != null) {
                        return doubleCheckValue;
                    }

                    // 执行数据加载
                    T result = asyncSQLWrapper.executeAspectMethod(param, joinPoint);

                    // 回填缓存
                    if (result != null || Boolean.TRUE.equals(param.getCacheNull())) {
                        boundUtil.cacheResult(param);
                    }

                    return result;

                } finally {
                    lock.releaseLock(key);
                }
            } else {
                Thread.sleep(100);
                // 获取锁失败，降级为直接加载
                return loadWithLock(param, joinPoint);
            }
        } catch (Exception e) {
            log.error("尝试直接从数据库获取: {}", key);
            try {
                return (T) joinPoint.proceed();
            } catch (Throwable t) {
                log.error("数据库加载失败: {}", key, t);
                throw new RuntimeException(t);
            }
        }
    }

    @Override
    public ReadStrategyType getStrategyType() {
        return ReadStrategyType.LAZY_LOAD;
    }

}
