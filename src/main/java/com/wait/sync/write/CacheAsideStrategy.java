package com.wait.sync.write;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.WriteStrategyType;
import com.wait.util.AsyncSQLWrapper;
import com.wait.util.BoundUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;

/**
 * case aside同步模式，适用于大多数读多写少的场景
 * */
@Component
@Slf4j
@RequiredArgsConstructor
public class CacheAsideStrategy implements WriteStrategy {

    private final BoundUtil boundUtil;

    private final AsyncSQLWrapper asyncSQLWrapper;

    @Override
    public void write(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        try {
            // 1. 先执行数据库操作
            asyncSQLWrapper.executeAspectMethod(param, joinPoint);

            // 2. 删除缓存（Cache-Aside模式）
            boundUtil.del(param.getKey());

            log.debug("Cache-Aside write strategy executed, key: {}", param.getKey());

        } catch (Exception e) {
            log.error("Cache-Aside write strategy executed with error, key: {}", param.getKey(), e);
            throw new RuntimeException("write failed", e);
        }
    }

    @Override
    public void delete(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        try {
            // 1. 先执行数据库删除
            asyncSQLWrapper.executeAspectMethod(param, joinPoint);

            // 2. 删除缓存
            boundUtil.del(param.getKey());

            log.debug("Cache-Aside delete strategy executed: {}", param.getKey());
        } catch (Exception e) {
            log.error("Cache-Aside delete strategy executed with error, key: {}", param.getKey(), e);
            throw new RuntimeException("delete failed", e);
        }
    }

    @Override
    public WriteStrategyType getStrategyType() {
        return WriteStrategyType.CACHE_ASIDE;
    }
}
