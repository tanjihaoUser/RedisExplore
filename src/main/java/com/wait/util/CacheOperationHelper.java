package com.wait.util;

import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.CacheType;
import com.wait.entity.type.ReadStrategyType;
import com.wait.entity.type.WriteStrategyType;
import com.wait.sync.CacheStrategyFactory;
import com.wait.sync.MethodExecutor;
import com.wait.sync.ProceedingJoinPointMethodExecutor;
import com.wait.sync.read.ReadStrategy;
import com.wait.sync.write.WriteStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 缓存操作辅助类
 * 封装策略类的使用，简化service层的缓存操作
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CacheOperationHelper {

    private final CacheStrategyFactory cacheStrategyFactory;

    /**
     * 执行缓存读取操作
     */
    public <T> T read(String key, Class<T> clazz, ReadStrategyType readStrategy, MethodExecutor methodExecutor) {
        CacheSyncParam<T> param = CacheSyncParam.<T>builder()
                .key(key)
                .clazz(clazz)
                .cacheType(CacheType.STRING)
                .cacheNull(true)
                .build();

        ReadStrategy strategy = cacheStrategyFactory.getReadStrategy(readStrategy);
        return strategy.read(param, methodExecutor);
    }

    /**
     * 执行缓存写入操作
     */
    public <T> void write(String key, T value, WriteStrategyType writeStrategy,
            MethodExecutor methodExecutor, Integer expireTime, TimeUnit timeUnit) {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) (value != null ? value.getClass() : Object.class);

        CacheSyncParam<T> param = CacheSyncParam.<T>builder()
                .key(key)
                .newValue(value)
                .clazz(clazz)
                .cacheType(CacheType.STRING)
                .expireTime(expireTime)
                .timeUnit(timeUnit)
                .cacheNull(false)
                .build();

        WriteStrategy strategy = cacheStrategyFactory.getWriteStrategy(writeStrategy);
        strategy.write(param, methodExecutor);
    }

    /**
     * 执行缓存删除操作
     */
    public void delete(String key, WriteStrategyType writeStrategy, MethodExecutor methodExecutor) {
        CacheSyncParam<?> param = CacheSyncParam.builder()
                .key(key)
                .cacheType(CacheType.STRING)
                .build();

        WriteStrategy strategy = cacheStrategyFactory.getWriteStrategy(writeStrategy);
        strategy.delete(param, methodExecutor);
    }

    /**
     * 从ProceedingJoinPoint创建MethodExecutor并执行读取
     */
    public <T> T readFromJoinPoint(String key, Class<T> clazz, ReadStrategyType readStrategy,
            ProceedingJoinPoint joinPoint) {
        MethodExecutor methodExecutor = new ProceedingJoinPointMethodExecutor(joinPoint);
        return read(key, clazz, readStrategy, methodExecutor);
    }

    /**
     * 从ProceedingJoinPoint创建MethodExecutor并执行写入
     */
    public <T> void writeFromJoinPoint(String key, T value, WriteStrategyType writeStrategy,
            ProceedingJoinPoint joinPoint, Integer expireTime, TimeUnit timeUnit) {
        MethodExecutor methodExecutor = new ProceedingJoinPointMethodExecutor(joinPoint);
        write(key, value, writeStrategy, methodExecutor, expireTime, timeUnit);
    }

    /**
     * 从ProceedingJoinPoint创建MethodExecutor并执行删除
     */
    public void deleteFromJoinPoint(String key, WriteStrategyType writeStrategy, ProceedingJoinPoint joinPoint) {
        MethodExecutor methodExecutor = new ProceedingJoinPointMethodExecutor(joinPoint);
        delete(key, writeStrategy, methodExecutor);
    }
}
