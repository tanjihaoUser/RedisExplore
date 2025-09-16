package com.wait.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 使用BoundXxxOperations处理key和value，根据类型自动判断调用什么方法
 * */
@Component
public class BoundUtil {

    private static final Logger log = LoggerFactory.getLogger(BoundUtil.class);

    @Autowired
    private StringRedisTemplate template;

    /* ========== String（BoundValueOperations） ========== */
    private BoundValueOperations<String, String> boundStr(String key) {
        return template.boundValueOps(key);
    }

    public void set(String key, String value) {
        boundStr(key).set(value);
    }

    public String get(String key) {
        String value = boundStr(key).get();
        if (value == null) log.warn("key {} not exist, return Null", key);
        return value;
    }

    public void mSet(Map<String, String> data) {
        template.opsForValue().multiSet(data);
    }

    public List<String> mGet(List<String> keys) {
        List<String> res = template.opsForValue().multiGet(keys);
        if (res == null) return Collections.emptyList();
        List<String> transRes = new ArrayList<>();
        for (int i = 0; i < res.size(); i++) {
            String value = res.get(i);
            if (value == null) log.warn("key {} not exist", keys.get(i));
            transRes.add(value);
        }
        return transRes;
    }

    public Boolean setNx(String key, String value) {
        return boundStr(key).setIfAbsent(value);
    }

    public Boolean setNx(String key, String value, Duration ttl) {
        return boundStr(key).setIfAbsent(value, ttl);
    }

    public void setEx(String key, String value, Duration ttl) {
        boundStr(key).set(value, ttl);
    }

    public Long incr(String key) {
        return boundStr(key).increment(1);
    }

    public Long incrBy(String key, long delta) {
        return boundStr(key).increment(delta);
    }

    public Double incrByFloat(String key, double delta) {
        return boundStr(key).increment(delta);
    }

    /* ========== List（BoundListOperations） ========== */
    private BoundListOperations<String, String> boundList(String key) {
        return template.boundListOps(key);
    }

    public Long leftPush(String key, String... values) {
        return boundList(key).leftPushAll(values);
    }

    public Long rightPush(String key, String... values) {
        return boundList(key).rightPushAll(values);
    }

    public String leftPop(String key) {
        return boundList(key).leftPop();
    }

    public String rightPop(String key) {
        return boundList(key).rightPop();
    }

    public String blockLeftPop(String key, long timeout, TimeUnit unit) {
        return boundList(key).leftPop(timeout, unit);
    }

    public List<String> range(String key, long start, long end) {
        return boundList(key).range(start, end);
    }

    public Long listSize(String key) {
        return boundList(key).size();
    }

    public void trim(String key, long start, long end) {
        boundList(key).trim(start, end);
    }

    /* ========== Set（BoundSetOperations） ========== */
    private BoundSetOperations<String, String> boundSet(String key) {
        return template.boundSetOps(key);
    }

    public Long sAdd(String key, String... values) {
        return boundSet(key).add(values);
    }

    public Boolean sIsMember(String key, String value) {
        return boundSet(key).isMember(value);
    }

    public Set<String> sMembers(String key) {
        return boundSet(key).members();
    }

    public Long sRem(String key, String... values) {
        return boundSet(key).remove(values);
    }

    public Long sCard(String key) {
        return boundSet(key).size();
    }

    public String sPop(String key) {
        return boundSet(key).pop();
    }

    /* ========== ZSet（BoundZSetOperations） ========== */
    private BoundZSetOperations<String, String> boundZSet(String key) {
        return template.boundZSetOps(key);
    }

    public Boolean zAdd(String key, String value, double score) {
        return boundZSet(key).add(value, score);
    }

    public Double zIncrBy(String key, String value, double delta) {
        return boundZSet(key).incrementScore(value, delta);
    }

    public Set<String> zRange(String key, long start, long end) {
        return boundZSet(key).range(start, end);
    }

    public Set<String> zReverseRange(String key, long start, long end) {
        return boundZSet(key).reverseRange(start, end);
    }

    public Double zScore(String key, String value) {
        return boundZSet(key).score(value);
    }

    public Long zRem(String key, String... values) {
        return boundZSet(key).remove(values);
    }

    public Long zCard(String key) {
        return boundZSet(key).size();
    }

    /* ========== Hash（BoundHashOperations） ========== */
    private BoundHashOperations<String, String, String> boundHash(String key) {
        return template.boundHashOps(key);
    }

    public void hSet(String key, String field, String value) {
        boundHash(key).put(field, value);
    }

    public void hSetAll(String key, Map<String, String> map) {
        boundHash(key).putAll(map);
    }

    public String hGet(String key, String field) {
        String v = boundHash(key).get(field);
        if (v == null) log.warn("[hget] key={} field={} not exist", key, field);
        return v == null ? "" : v;
    }

    public Map<String, String> hGetAll(String key) {
        Map<String, String> map = boundHash(key).entries();
        return map == null ? Collections.emptyMap() : map;
    }

    public Long hDel(String key, String... fields) {
        return boundHash(key).delete(fields);
    }

    public Boolean hExists(String key, String field) {
        return boundHash(key).hasKey(field);
    }

    public Long hLen(String key) {
        return boundHash(key).size();
    }

    /* ========== 通用 key 管理 ========== */
    public Boolean exists(String key) {
        return template.hasKey(key);
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return template.expire(key, timeout, unit);
    }

    public Long getExpire(String key) {
        return template.getExpire(key);
    }

    public Boolean del(String key) {
        return template.delete(key);
    }

    public Long delMulti(String... keys) {
        return template.delete(Arrays.asList(keys));
    }
}
