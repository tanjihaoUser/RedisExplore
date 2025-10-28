package com.wait.aspect;

import com.wait.annotation.RedisCache;
import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.CacheType;
import com.wait.sync.CacheStrategyFactory;
import com.wait.sync.read.ReadStrategy;
import com.wait.sync.write.WriteStrategy;
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
                writeStrategy.write(cacheSyncParam, joinPoint);
                return null;
            default:
                return null;
        }
    }

}