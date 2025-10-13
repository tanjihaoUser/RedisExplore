package com.wait.aspect;

import com.wait.annotation.RedisCache;
import com.wait.entity.CacheType;
import com.wait.util.BoundUtil;
import com.wait.util.SpelExpressionParserUtil;
import com.wait.util.instance.HashMappingUtil;
import com.wait.util.instance.InstanceFactory;
import com.wait.util.lock.Lock;
import io.lettuce.core.RedisConnectionException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
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
    private HashMappingUtil hashMappingUtil;

    @Autowired
    private InstanceFactory instanceFactory;

    @Autowired
    private Lock lock;

    public static final String NULL_MARKER = ":null"; // 空值标记
    public static final int NULL_CACHE_TIME = 30; // 空值缓存时间
    public static final TimeUnit NULL_CACHE_TIME_UNIT = TimeUnit.SECONDS; // 空值缓存时间
    private final Random random = new Random();

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
            Object cachedValue = getFromCache(key, cacheType, clazz, cacheNull);
            if (cachedValue != null) {
                return cachedValue;
            }


            try {
                // 2. 缓存未命中，尝试获取分布式锁
                if (lock.getLock(key)) {
                    log.debug("获取锁成功, 查询数据库, key: {}", key);

                    // 3. 再次检查缓存（双重检查锁模式）
                    Object doubleCheckValue = getFromCache(key, cacheType, clazz, cacheNull);
                    if (doubleCheckValue != null) {
                        return doubleCheckValue;
                    }

                    // 4. 执行原方法（数据库查询）
                    Object result = joinPoint.proceed();

                    // 5. 缓存结果
                    cacheResult(key, result, cacheType, baseExpire, timeUnit, cacheNull);

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

    /**
     * 从缓存获取数据
     */
    private Object getFromCache(String key, CacheType cacheType, Class<?> clazz, boolean cacheNull) {
        Object result = null;
        try {
            switch (cacheType) {
                case STRING:
                    result = boundUtil.get(key, clazz);
                    break;
                case HASH:
                    result = boundUtil.hGetAll(key, clazz);
                    break;
                default:
                    log.warn("不支持的缓存类型: {}", cacheType);
                    return null;
            }
            if (result != null && !isNullMarker(result)) {
                // 缓存命中且不是空值标记
                log.info("hit cache, key: {}", key);
            } else if (isNullMarker(result) && cacheNull) {
                // 如果是空值标记且允许缓存null，返回空实例
                log.info("hit cache but null, key: {}", key);
                return createEmptyInstance(clazz);
            }
        } catch (Exception e) {
            log.warn("缓存读取异常, key: {}", key, e);
        }
        return result;
    }

    /**
     * 缓存结果
     */
    private void cacheResult(String key, Object result, CacheType cacheType, int baseExpire, TimeUnit timeUnit, boolean cacheNull) {
        try {
            if (result == null) {
                if (cacheNull) {
                    // 缓存空值
                    cacheNullValue(key, cacheType);
                }
                return;
            }

            // 设置随机过期时间，避免缓存雪崩
            int randomExpire = getRandomExpire(baseExpire);

            switch (cacheType) {
                case STRING:
                    boundUtil.set(key, result, randomExpire, timeUnit);
                    break;
                case HASH:
                    Map<String, Object> hashMap = hashMappingUtil.objectToMap(result);
                    boundUtil.hSetAll(key, hashMap, randomExpire, timeUnit);
                    break;
                default:
                    log.warn("不支持的缓存类型: {}", cacheType);
            }

            log.debug("缓存设置成功, key: {}, expire: {}{}", key, randomExpire, timeUnit);

        } catch (Exception e) {
            log.warn("缓存设置异常, key: {}", key, e);
        }
    }

    /**
     * 缓存空值（使用较短的过期时间）
     */
    private void cacheNullValue(String key, CacheType cacheType) {
        try {

            switch (cacheType) {
                case STRING:
                    boundUtil.set(key + NULL_MARKER, "NULL", NULL_CACHE_TIME, NULL_CACHE_TIME_UNIT);
                    break;
                case HASH:
                    Map<String, String> nullMap = new HashMap<>();
                    nullMap.put("_null", "true");
                    boundUtil.hSetAll(key + NULL_MARKER, nullMap, NULL_CACHE_TIME, NULL_CACHE_TIME_UNIT);
                    break;
            }

            log.debug("空值缓存设置成功, key: {}", key + NULL_MARKER);

        } catch (Exception e) {
            log.warn("空值缓存设置异常, key: {}", key, e);
        }
    }

    /**
     * 判断是否为空值标记
     */
    private boolean isNullMarker(Object value) {
        if (value == null) return false;

        if (value instanceof String) {
            return "NULL".equals(value);
        }

        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            return "true".equals(map.get("_null"));
        }

        return false;
    }

    /**
     * 创建空实例
     */
    private Object createEmptyInstance(Class<?> clazz) {
        try {
            return instanceFactory.createInstanceSafely(clazz);
        } catch (Exception e) {
            log.warn("创建空实例失败: {}", clazz.getName(), e);
            return null;
        }
    }

    /**
     * 获取随机过期时间（防雪崩）
     */
    private int getRandomExpire(int baseExpire) {
        // 在基础过期时间上增加随机偏移（±20%）
        int offset = (int) (baseExpire * 0.2);
        int randomOffset = random.nextInt(offset * 2) - offset;
        return Math.max(1, baseExpire + randomOffset); // 确保不小于1
    }
}