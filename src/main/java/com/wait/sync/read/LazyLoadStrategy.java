package com.wait.sync.read;

import com.wait.entity.CacheResult;
import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.ReadStrategyType;
import com.wait.util.AsyncSQLWrapper;
import com.wait.util.BoundUtil;
import com.wait.util.lock.Lock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;

/**
 * 懒加载，适用于大多数场景
 * */
@Component
@Slf4j
@RequiredArgsConstructor
public class LazyLoadStrategy implements ReadStrategy {

    private final BoundUtil boundUtil;

    private final Lock lock;

    private final AsyncSQLWrapper asyncSQLWrapper;

    @Override
    public <T> T read(CacheSyncParam<T> param, ProceedingJoinPoint joinPoint) {
        // 1. 先查缓存
        CacheResult<T> cachedValue = boundUtil.getWithRetry(param, 3);
        if (cachedValue.isHit()) {
            log.debug("lazy load hit cache, key: {}, value: {}", param.getKey(), cachedValue.getValue());
            return cachedValue.getValue();
        }

        log.info("lazy load miss cache: {}, loading...", param.getKey());
        // 2. 缓存未命中，加锁加载
        return loadWithLock(param, joinPoint);
    }

    /**
     * 这里只是逻辑，具体的异常需要业务层处理
     * */
    private <T> T loadWithLock(CacheSyncParam<T> param, ProceedingJoinPoint joinPoint) {

        String key = param.getKey();
        if (lock.getLock(key)) {
            try {
                // 双重检查
                CacheResult<T> doubleCheckValue = boundUtil.getFromCache(param);
                if (doubleCheckValue.isHit()) {
                    return doubleCheckValue.getValue();
                }

                // 执行数据加载
                asyncSQLWrapper.executeAspectMethod(param, joinPoint);
                log.debug("database op execute success, res: {}", param.getNewValue());

                // 回填缓存
                if (param.getNewValue() != null || Boolean.TRUE.equals(param.getCacheNull())) {
                    boundUtil.writeWithRetry(param, 3);
                    log.debug("lazy load write cache: {}", param.getKey());
                }

                return param.getNewValue();

            } finally {
                lock.releaseLock(key);
            }
        } else {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("lazy load sleep interrupted error: {}", param.getKey(), e);
            }
            // 获取锁失败，重试
            return loadWithLock(param, joinPoint);
        }
    }

    @Override
    public ReadStrategyType getStrategyType() {
        return ReadStrategyType.LAZY_LOAD;
    }

}
