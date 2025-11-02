package com.wait.aspect;

import com.wait.annotation.RedisCache;
import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.WriteStrategyType;
import com.wait.sync.CacheStrategyFactory;
import com.wait.sync.read.ReadStrategy;
import com.wait.sync.write.WriteStrategy;
import com.wait.util.SpelExpressionParserUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class RedisCacheAspect {

    @Autowired
    private SpelExpressionParserUtil spelExpressionParserUtil;

    @Autowired
    private CacheStrategyFactory cacheStrategyFactory;

    @Around("@annotation(redisCache)")
    public Object handleCache(ProceedingJoinPoint joinPoint, RedisCache redisCache) throws Throwable {
        String key = spelExpressionParserUtil.generateCacheKey(joinPoint, redisCache.key(), redisCache.prefix());
        log.debug("aspect handle cache, key: {}, redisCache: {}", key, redisCache);
        CacheSyncParam<Object> cacheSyncParam = CacheSyncParam.getFromRedisCache(key, redisCache);

        switch (redisCache.operation()) {
            case SELECT:
                ReadStrategy strategy = cacheStrategyFactory.getReadStrategy(redisCache.readStrategy());
                return strategy.read(cacheSyncParam, joinPoint);
            case UPDATE:
            case DELETE:
                WriteStrategy writeStrategy = cacheStrategyFactory.getWriteStrategy(redisCache.writeStrategy());
                // 对于写回策略（INCREMENTAL_WRITE_BEHIND 和 SNAPSHOT_WRITE_BEHIND），不立即执行数据库操作
                // 只更新Redis和缓冲任务，由定时任务统一批量写入数据库
                if (redisCache.writeStrategy() == WriteStrategyType.INCREMENTAL_WRITE_BEHIND ||
                    redisCache.writeStrategy() == WriteStrategyType.SNAPSHOT_WRITE_BEHIND) {
                    // 只调用写策略进行Redis更新和后续缓冲，不执行数据库操作
                    // 由定时任务统一执行数据库写入
                    writeStrategy.write(cacheSyncParam, joinPoint);
                    // 由于未执行实际方法，需要返回合适的默认值，避免返回null导致原始方法签名不匹配
                    return getDefaultReturnValue(joinPoint);
                } else {
                    // 其他写策略：策略类内部会执行数据库操作
                    writeStrategy.write(cacheSyncParam, joinPoint);
                    // 返回目标方法的执行结果（对于非void方法如int/Integer，避免返回null导致原始方法签名不匹配）
                    Object result = cacheSyncParam.getNewValue();
                    // 如果策略未设置返回值，返回默认值（兼容某些策略可能不设置返回值的情况）
                    if (result == null) {
                        return getDefaultReturnValue(joinPoint);
                    }
                    return result;
                }
            default:
                return null;
        }
    }

    /**
     * 获取方法的默认返回值
     * 对于写回策略（INCREMENTAL_WRITE_BEHIND、SNAPSHOT_WRITE_BEHIND），由于不立即执行数据库操作，需要返回合适的默认值
     * 避免返回null导致原始方法签名不匹配（特别是对于原始返回类型如 int）
     */
    private Object getDefaultReturnValue(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> returnType = signature.getReturnType();
        
        // 如果返回类型是基本类型，返回对应的默认值；否则返回null
        if (returnType == void.class || returnType == Void.class) {
            return null;
        } else if (returnType == int.class || returnType == Integer.class) {
            return 0; // MyBatis update/delete通常返回受影响行数，0表示还未执行
        } else if (returnType == long.class || returnType == Long.class) {
            return 0L;
        } else if (returnType == boolean.class || returnType == Boolean.class) {
            return false;
        } else {
            return null;
        }
    }

}