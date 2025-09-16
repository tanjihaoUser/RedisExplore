package com.wait.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 使用StringRedisTemplate.opsForXxx()进行读写，value是string类型的工具类
 * */
@Component
public class RTForString {

    private static final Logger log = LoggerFactory.getLogger(RTForString.class);
    @Autowired
    private StringRedisTemplate redis;

    /* --------------- 写操作 --------------- */
    /* 1. SET key value */
    public void set(String key, String value) {
        redis.opsForValue().set(key, value);
    }

    /* 3. MSET k1 v1 k2 v2 ... */
    public void mset(Map<String, String> kvs) {
        redis.opsForValue().multiSet(kvs);
    }

    /* 8. SETNX key value */
    public Boolean setNx(String key, String value) {
        return redis.opsForValue().setIfAbsent(key, value);
    }

    /* 8. SETNX key value + 过期时间（原子）*/
    public Boolean setNx(String key, String value, Duration ttl) {
        return redis.opsForValue().setIfAbsent(key, value, ttl);
    }

    /* 9. SETEX key value ttl */
    public void setEx(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    /* --------------- 读操作（空值保护） --------------- */
    /* 2. GET key */
    public String get(String key) {
        String v = redis.opsForValue().get(key);
        if (v == null) {
            log.warn("[get] key={} 不存在，返回空字符串", key);
        }
        return v == null ? "None" : v;
    }

    /* 4. MGET k1 k2 k3 */
    public List<String> mget(List<String> keys) {
        List<String> list = redis.opsForValue().multiGet(keys);
        if (list == null) {
            log.warn("[mget] 返回 null，转空列表");
            return Collections.emptyList();
        }
        // Redis 对不存在的 key 会放 null 占位，这里统一替换成 ""
        List<String> safe = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            String v = list.get(i);
            if (v == null) {
                log.warn("[mget] 下标={} key={} 不存在，替换为空串", i, keys.get(i));
                safe.add("None");
            } else {
                safe.add(v);
            }
        }
        return safe;
    }

    /* --------------- 数字自增 --------------- */
    // key不存在不会抛异常，Redis会当做-自增返回结果
    private boolean checkIncrKey(String key) {
        if (Boolean.FALSE.equals(redis.hasKey(key))) {
            log.warn("[incr] key={} 不存在，Redis 将按 0 自增", key);
            return false;
        }
        return true;
    }

    /* 5. INCR key */
    public Long incr(String key) {
        checkIncrKey(key);
        return redis.opsForValue().increment(key);
    }

    /* 6. INCRBY key delta */
    public Long incrBy(String key, long delta) {
        checkIncrKey(key);
        return redis.opsForValue().increment(key, delta);
    }

    /* 7. INCRBYFLOAT key delta */
    public Double incrByFloat(String key, double delta) {
        checkIncrKey(key);
        return redis.opsForValue().increment(key, delta);
    }

}
