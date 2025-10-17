package com.wait.aspect;

import com.wait.annotation.RedisCache;
import com.wait.entity.CacheType;
import com.wait.util.BoundUtil;
import com.wait.util.SpelExpressionParserUtil;
import com.wait.util.bloomfilter.IBloomFilter;
import com.wait.util.lock.Lock;
import io.lettuce.core.RedisConnectionException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class RedisCacheAspect {

    @Autowired
    private BoundUtil boundUtil;

    @Autowired
    private SpelExpressionParserUtil spelExpressionParserUtil;

    @Autowired
    private Lock lock;

    @Autowired
    @Qualifier("guavaBloomFilter")
    private IBloomFilter bloomFilter;

    @Around("@annotation(redisCache)")
    public Object getCache(ProceedingJoinPoint joinPoint, RedisCache redisCache) throws Throwable {
        String key = spelExpressionParserUtil.generateCacheKey(joinPoint, redisCache.key(), redisCache.prefix());
        CacheType cacheType = redisCache.cacheType();
        Class<?> clazz = redisCache.returnType();
        boolean cacheNull = redisCache.isCacheNull();
        int baseExpire = redisCache.expire();
        TimeUnit timeUnit = redisCache.timeUnit();

        try {
            // 1. 先尝试从缓存获取
            Object cachedValue = boundUtil.getFromCache(key, cacheType, clazz);
            if (cachedValue != null) {
                log.info("hit cache, key: {}, value: {}", key, cachedValue);
                return cachedValue;
            }


            try {
                // 2. 缓存未命中，尝试获取分布式锁
                if (lock.getLock(key)) {
                    log.info("get lock success and query db, key: {}", key);

                    // 3. 再次检查缓存（双重检查锁模式）
                    Object doubleCheckValue = boundUtil.getFromCache(key, cacheType, clazz);
                    if (doubleCheckValue != null) {
                        return doubleCheckValue;
                    }

                    // 4. 执行原方法（数据库查询）
                    Object result = joinPoint.proceed();
                    log.info("get data from databse, result: {}", result);

                    // 5. 缓存结果
                    boundUtil.cacheResult(key, result, cacheType, baseExpire, timeUnit, cacheNull);

                    return result;

                } else {
                    // 获取锁失败，等待并重试
                    log.debug("获取锁失败，等待后重试, key: {}", key);
                    Thread.sleep(100);
                    return getCache(joinPoint, redisCache);
                }

            } finally {
                lock.releaseLock(key);
            }

        } catch (RedisConnectionException e) {
            log.error("Redis连接异常, key: {}, 直接查询数据库", key, e);
            return joinPoint.proceed();
        } catch (Exception e) {
            log.error("未知缓存处理异常, key: {}", key, e);
            throw e; // 重新抛出，避免掩盖业务异常
        }
    }

}