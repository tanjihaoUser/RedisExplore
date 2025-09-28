package com.wait.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 支持任意类型的 Redis 工具类
 * 使用泛型支持多种数据类型，包括 String、Integer、Float、自定义对象等
 */
@Component
public class BoundUtil {

    private static final Logger log = LoggerFactory.getLogger(BoundUtil.class);

    @Autowired
    private StringRedisTemplate stringTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private Map<String, DefaultRedisScript<Long>> luaScriptMap;

    // 序列化器
    private final RedisSerializer<String> stringSerializer = RedisSerializer.string();
    private final RedisSerializer<Object> valueSerializer = RedisSerializer.java();

    /**
     * 将对象序列化为字符串
     */
    private String serializeValue(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        try {
            byte[] bytes = valueSerializer.serialize(value);
            return bytes != null ? new String(bytes) : null;
        } catch (SerializationException e) {
            log.error("序列化失败: {}", value, e);
            throw new RuntimeException("序列化失败", e);
        }
    }

    /**
     * 将字符串反序列化为指定类型
     */
    @SuppressWarnings("unchecked")
    private <T> T deserializeValue(String value, Class<T> clazz) {
        if (value == null) {
            return null;
        }
        // 如果是 String 类型，直接返回
        if (clazz.equals(String.class)) {
            return (T) value;
        }

        try {
            byte[] bytes = value.getBytes();
            Object result = valueSerializer.deserialize(bytes);
            if (result != null && !clazz.isInstance(result)) {
                throw new ClassCastException("类型不匹配: 期望 " + clazz.getName() + ", 实际 " + result.getClass().getName());
            }
            return (T) result;
        } catch (SerializationException e) {
            log.error("反序列化失败: {}", value, e);
            throw new RuntimeException("反序列化失败", e);
        }
    }

    /**
     * 安全类型转换
     */
    @SuppressWarnings("unchecked")
    private <T> T safeCast(Object obj, Class<T> clazz) {
        if (obj == null) {
            return null;
        }
        if (!clazz.isInstance(obj)) {
            throw new ClassCastException("类型不匹配: 期望 " + clazz.getName() + ", 实际 " + obj.getClass().getName());
        }
        return (T) obj;
    }

    public Long executeScriptByMap(String scriptName, List<String> keys, Object... args) {
        DefaultRedisScript<Long> script = luaScriptMap.get(scriptName);
        if (script == null) {
            throw new IllegalArgumentException("未找到脚本: " + scriptName);
        }

        Long result = redisTemplate.execute(script, keys, args);
//        if (result == null) {
//            if (returnType.isPrimitive()) {
//                throw new IllegalArgumentException("Lua 脚本返回 null，但期望是基本类型 " + returnType.getName());
//            }
//            return null;
//        }

//        return safeCast(result, returnType);
        return result;
    }

    public <T> T executeSpecificScript(DefaultRedisScript<T> script, List<String> keys, Object... args) {
        return redisTemplate.execute(script, keys, args);
    }
    /* ========== String（BoundValueOperations） ========== */
    private BoundValueOperations<String, Object> boundValue(String key) {
        return redisTemplate.boundValueOps(key);
    }

    public <T> void set(String key, T value) {
        boundValue(key).set(value);
    }

    public <T> T get(String key, Class<T> clazz) {
        Object value = boundValue(key).get();
        if (value == null) {
            log.warn("key {} not exist, return Null", key);
            return null;
        }
        return safeCast(value, clazz);
    }

    public <T> void mSet(Map<String, T> data) {
        redisTemplate.opsForValue().multiSet(data);
    }

    public <T> List<T> mGet(List<String> keys, Class<T> clazz) {
        List<Object> res = redisTemplate.opsForValue().multiGet(keys);
        if (res == null) return Collections.emptyList();

        List<T> result = new ArrayList<>();
        for (int i = 0; i < res.size(); i++) {
            Object value = res.get(i);
            if (value == null) {
                log.warn("key {} not exist", keys.get(i));
                result.add(null);
            } else {
                result.add(safeCast(value, clazz));
            }
        }
        return result;
    }

    public <T> Boolean setNx(String key, T value) {
        return boundValue(key).setIfAbsent(value);
    }

    public <T> Boolean setNx(String key, T value, Duration ttl) {
        return boundValue(key).setIfAbsent(value, ttl);
    }

    public <T> void setEx(String key, T value, Duration ttl) {
        boundValue(key).set(value, ttl);
    }

    public Long incr(String key) {
        return boundValue(key).increment(1);
    }

    public Long incrBy(String key, long delta) {
        return boundValue(key).increment(delta);
    }

    public Double incrByFloat(String key, double delta) {
        return boundValue(key).increment(delta);
    }
    /* ========== List（BoundListOperations） ========== */
    private BoundListOperations<String, Object> boundList(String key) {
        return redisTemplate.boundListOps(key);
    }

    @SafeVarargs
    public final <T> Long leftPush(String key, T... values) {
        return boundList(key).leftPushAll(values);
    }

    @SafeVarargs
    public final <T> Long rightPush(String key, T... values) {
        return boundList(key).rightPushAll(values);
    }

    public <T> T leftPop(String key, Class<T> clazz) {
        Object value = boundList(key).leftPop();
        return value != null ? safeCast(value, clazz) : null;
    }

    public <T> T rightPop(String key, Class<T> clazz) {
        Object value = boundList(key).rightPop();
        return value != null ? safeCast(value, clazz) : null;
    }

    public <T> T blockLeftPop(String key, long timeout, TimeUnit unit, Class<T> clazz) {
        Object value = boundList(key).leftPop(timeout, unit);
        return value != null ? safeCast(value, clazz) : null;
    }

    public <T> List<T> range(String key, long start, long end, Class<T> clazz) {
        List<Object> values = boundList(key).range(start, end);
        if (values == null) return Collections.emptyList();

        List<T> result = new ArrayList<>();
        for (Object value : values) {
            result.add(safeCast(value, clazz));
        }
        return result;
    }

    public Long listSize(String key) {
        return boundList(key).size();
    }

    public void trim(String key, long start, long end) {
        boundList(key).trim(start, end);
    }
    /* ========== Set（BoundSetOperations） ========== */
    private BoundSetOperations<String, Object> boundSet(String key) {
        return redisTemplate.boundSetOps(key);
    }

    @SafeVarargs
    public final <T> Long sAdd(String key, T... values) {
        return boundSet(key).add(values);
    }

    public <T> Boolean sIsMember(String key, T value) {
        return boundSet(key).isMember(value);
    }

    public <T> Set<T> sMembers(String key, Class<T> clazz) {
        Set<Object> members = boundSet(key).members();
        if (members == null) return Collections.emptySet();

        Set<T> result = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        for (Object member : members) {
            result.add(safeCast(member, clazz));
        }
        return result;
    }

    @SafeVarargs
    public final <T> Long sRem(String key, T... values) {
        return boundSet(key).remove(values);
    }

    public Long sCard(String key) {
        return boundSet(key).size();
    }

    public <T> T sPop(String key, Class<T> clazz) {
        Object value = boundSet(key).pop();
        return value != null ? safeCast(value, clazz) : null;
    }
    /* ========== ZSet（BoundZSetOperations） ========== */
    private BoundZSetOperations<String, Object> boundZSet(String key) {
        return redisTemplate.boundZSetOps(key);
    }

    public <T> Boolean zAdd(String key, T value, double score) {
        return boundZSet(key).add(value, score);
    }

    public <T> Double zIncrBy(String key, T value, double delta) {
        return boundZSet(key).incrementScore(value, delta);
    }

    public <T> Set<T> zRange(String key, long start, long end, Class<T> clazz) {
        Set<Object> values = boundZSet(key).range(start, end);
        if (values == null) return Collections.emptySet();

        Set<T> result = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        for (Object value : values) {
            result.add(safeCast(value, clazz));
        }
        return result;
    }

    public <T> Set<T> zReverseRange(String key, long start, long end, Class<T> clazz) {
        Set<Object> values = boundZSet(key).reverseRange(start, end);
        if (values == null) return Collections.emptySet();

        Set<T> result = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        for (Object value : values) {
            result.add(safeCast(value, clazz));
        }
        return result;
    }

    public <T> Double zScore(String key, T value) {
        return boundZSet(key).score(value);
    }

    @SafeVarargs
    public final <T> Long zRem(String key, T... values) {
        return boundZSet(key).remove(values);
    }

    public Long zCard(String key) {
        return boundZSet(key).size();
    }
    /* ========== Hash（BoundHashOperations） ========== */
    private BoundHashOperations<String, Object, Object> boundHash(String key) {
        return redisTemplate.boundHashOps(key);
    }

    public <K, V> void hSet(String key, K field, V value) {
        boundHash(key).put(field, value);
    }

    public <K, V> void hSetAll(String key, Map<K, V> map) {
        boundHash(key).putAll(map);
    }

    public <K, V> V hGet(String key, K field, Class<V> clazz) {
        Object value = boundHash(key).get(field);
        if (value == null) {
            log.warn("[hget] key={} field={} not exist", key, field);
            return null;
        }
        return safeCast(value, clazz);
    }

    public <K, V> Map<K, V> hGetAll(String key, Class<K> keyClass, Class<V> valueClass) {
        Map<Object, Object> map = boundHash(key).entries();
        if (map == null) return Collections.emptyMap();

        Map<K, V> result = new java.util.HashMap<>();
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            K k = safeCast(entry.getKey(), keyClass);
            V v = safeCast(entry.getValue(), valueClass);
            result.put(k, v);
        }
        return result;
    }

    @SafeVarargs
    public final <K> Long hDel(String key, K... fields) {
        return boundHash(key).delete(fields);
    }

    public <K> Boolean hExists(String key, K field) {
        return boundHash(key).hasKey(field);
    }

    public Long hLen(String key) {
        return boundHash(key).size();
    }
    /* ========== 通用 key 管理 ========== */
    public Boolean exists(String key) {
        return redisTemplate.hasKey(key);
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    public Long getExpire(String key) {
        return redisTemplate.getExpire(key);
    }

    public Boolean del(String key) {
        return redisTemplate.delete(key);
    }

    public Long delMulti(String... keys) {
        return redisTemplate.delete(Arrays.asList(keys));
    }
    /* ========== 便捷方法 ========== */
    // String 类型的便捷方法（保持向后兼容）
    public void setString(String key, String value) {
        set(key, value);
    }

    public String getString(String key) {
        return get(key, String.class);
    }
    // Integer 类型的便捷方法
    public void setInt(String key, Integer value) {
        set(key, value);
    }

    public Integer getInt(String key) {
        return get(key, Integer.class);
    }
    // Long 类型的便捷方法
    public void setLong(String key, Long value) {
        set(key, value);
    }

    public Long getLong(String key) {
        return get(key, Long.class);
    }
    // Double 类型的便捷方法
    public void setDouble(String key, Double value) {
        set(key, value);
    }

    public Double getDouble(String key) {
        return get(key, Double.class);
    }
}