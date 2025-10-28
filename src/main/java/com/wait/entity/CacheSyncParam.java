package com.wait.entity;

import com.wait.annotation.RedisCache;
import com.wait.entity.type.CacheType;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Data
@Builder
@Slf4j
public class CacheSyncParam<T> {

    // 必填字段
    String key;
    T newValue;
    private Integer expireTime;
    private TimeUnit timeUnit;
    private Boolean cacheNull;
    private CacheType cacheType;
    private Class<T> clazz;
    private Boolean isExecuteASync;

    // 可选字段
    private String messageTopic;
    private Integer refreshInterval; // 刷新间隔（用于定时刷新策略）
    private T result;

    public static CacheSyncParam getFromRedisCache(String key, RedisCache cache) {
        return CacheSyncParam.builder()
                .key(key)
                .newValue(null)
                .expireTime(cache.expire())
                .timeUnit(cache.timeUnit())
                .cacheNull(cache.isCacheNull())
                .cacheType(cache.cacheType())
                .clazz((Class<Object>) cache.returnType())
                .refreshInterval(10000)
                .build();
    }

}
