package com.wait.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.CacheType;
import com.wait.util.instance.HashMappingUtil;
import com.wait.util.instance.InstanceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 支持任意类型的 Redis 工具类
 * 使用泛型支持多种数据类型，包括 String、Integer、Float、自定义对象等
 */
@Component
@Slf4j
public class BoundUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private Map<String, DefaultRedisScript<Long>> luaScriptMap;

    @Autowired
    private HashMappingUtil hashMappingUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InstanceFactory instanceFactory;

    public static final int NULL_CACHE_TIME = 30; // 空值缓存时间
    public static final TimeUnit NULL_CACHE_TIME_UNIT = TimeUnit.SECONDS; // 空值缓存时间
    private final Random random = new Random();

    /**
     * 从缓存获取数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getFromCache(String key, CacheType cacheType, Class<T> clazz) {
        T result = null;
        try {
            switch (cacheType) {
                case STRING:
                    result = get(key, clazz);
                    break;
                case HASH:
                    result = hGetAll(key, clazz);
                    break;
                default:
                    log.warn("不支持的缓存类型: {}", cacheType);
                    return null;
            }
        } catch (Exception e) {
            log.warn("缓存读取异常, key: {}", key, e);
        }
        return result;
    }

    /**
     * 缓存结果
     */
    public void cacheResult(String key, Object result, CacheType cacheType, int baseExpire, TimeUnit timeUnit, boolean cacheNull) {
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
                    set(key, result, randomExpire, timeUnit);
                    break;
                case HASH:
                    Map<String, Object> hashMap = hashMappingUtil.objectToMap(result);
                    hSetAll(key, hashMap, randomExpire, timeUnit);
                    break;
                default:
                    log.warn("不支持的缓存类型: {}", cacheType);
            }

            log.debug("缓存设置成功, key: {}, expire: {}{}", key, randomExpire, timeUnit);

        } catch (Exception e) {
            log.warn("缓存设置异常, key: {}", key, e);
        }
    }


    public <T> T getFromCache(CacheSyncParam<T> param) {
        return getFromCache(param.getKey(), param.getCacheType(), param.getClazz());
    }
    
    public void cacheResult(CacheSyncParam param) {
        cacheResult(param.getKey(), param.getNewValue(), param.getCacheType(), param.getExpireTime(), param.getTimeUnit(), param.getCacheNull());
    }

    /**
     * 缓存空值（使用较短的过期时间）
     */
    private void cacheNullValue(String key, CacheType cacheType) {
        try {

            switch (cacheType) {
                case STRING:
                    set(key, "NULL", NULL_CACHE_TIME, NULL_CACHE_TIME_UNIT);
                    break;
                case HASH:
                    Map<String, String> nullMap = new HashMap<>();
                    nullMap.put("_null", "true");
                    hSetAll(key, nullMap, NULL_CACHE_TIME, NULL_CACHE_TIME_UNIT);
                    break;
            }

            log.info("空值缓存设置成功, key: {}", key);

        } catch (Exception e) {
            log.warn("空值缓存设置异常, key: {}", key, e);
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
     * 安全类型转换
     */
    @SuppressWarnings("unchecked")
    private <T> T safeCast(Object value, Class<T> clazz) {
        if (value == null) {
            return null;
        }

        // 1. 如果类型匹配，直接返回
        if (clazz.isInstance(value)) {
            return (T) value;
        }

        // 2. 如果是String，尝试JSON反序列化
        if (value instanceof String) {
            String strValue = (String) value;

            // 检查是否是JSON格式
            if (isJsonString(strValue)) {
                try {
                    return objectMapper.readValue(strValue, clazz);
                } catch (Exception e) {
                    log.warn("JSON反序列化失败, 目标类型: {}", clazz.getName(), e);
                }
            }
        }

        // 3. 缓存命中空值标记，返回空实例
        if (isNullMarker(value)) {
            return (T) createEmptyInstance(clazz);
        }

        // 4. 使用Jackson进行类型转换
        try {
            return objectMapper.convertValue(value, clazz);
        } catch (Exception e) {
            log.warn("类型转换失败, 值类型: {}, 目标类型: {}",
                    value.getClass().getName(), clazz.getName(), e);

            // 最后尝试强制转换
            try {
                return (T) value;
            } catch (ClassCastException ex) {
                throw new RuntimeException("无法将 " + value.getClass() + " 转换为 " + clazz, ex);
            }
        }
    }

    private boolean isJsonString(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        String trimmed = str.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    public Long executeScriptByMap(String scriptName, List<String> keys, Object... args) {
        DefaultRedisScript<Long> script = luaScriptMap.get(scriptName);
        if (script == null) {
            throw new IllegalArgumentException("未找到脚本: " + scriptName);
        }

        Long result = redisTemplate.execute(script, keys, args);
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

    public <T> void set(String key, T value, long timeout, TimeUnit timeUnit) {
        try {
            if (timeout <= 0) {
                redisTemplate.opsForValue().set(key, value);
            } else {
                redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
            }
        } catch (Exception e) {
            log.error("设置缓存失败, key: {}", key, e);
        }
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

    public <T> void hSetAll(String key, Map<String, T> hashMap, long timeout, TimeUnit timeUnit) {
        try {
            redisTemplate.opsForHash().putAll(key, hashMap);
            if (timeout > 0) {
                redisTemplate.expire(key, timeout, timeUnit);
            }
        } catch (Exception e) {
            log.error("设置Hash缓存失败, key: {}", key, e);
        }
    }

    public <K, V> V hGet(String key, K field, Class<V> clazz) {
        Object value = boundHash(key).get(field);
        if (value == null) {
            log.warn("[hget] key={} field={} not exist", key, field);
            return null;
        }
        return safeCast(value, clazz);
    }

    public <T> T hGetAll(String key, Class<T> clazz) {
        Map<Object, Object> map = boundHash(key).entries();
        if (map == null || map.isEmpty()) {
            log.debug("Hash key {} not found or empty", key);
            return null;
        }

        // 将Object,Object的Map转换为String,Object的Map
        Map<String, Object> stringKeyMap = new HashMap<>();
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            stringKeyMap.put(entry.getKey().toString(), entry.getValue());
        }

        // 映射为对象
        return hashMappingUtil.mapToObject(stringKeyMap, clazz);
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
        try {
            return redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("删除key失败, key: {}", key, e);
            return false;
        }
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