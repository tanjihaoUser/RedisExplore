package com.wait.util;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wait.entity.CacheResult;
import com.wait.entity.CacheSyncParam;
import com.wait.entity.NullObject;
import com.wait.entity.type.CacheType;
import com.wait.exception.CacheOperationException;
import com.wait.util.instance.HashMappingUtil;
import com.wait.util.instance.InstanceFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 支持任意类型的 Redis 工具类
 * 使用泛型支持多种数据类型，包括 String、Integer、Float、自定义对象等
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BoundUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    private final Map<String, DefaultRedisScript<Long>> luaScriptMap;

    private final HashMappingUtil hashMappingUtil;

    private final ObjectMapper objectMapper;

    private final InstanceFactory instanceFactory;

    @Qualifier("retryExecutor")
    private final ThreadPoolTaskExecutor retryExecutor;

    public static final int NULL_CACHE_TIME = 30; // 空值缓存时间
    public static final TimeUnit NULL_CACHE_TIME_UNIT = TimeUnit.SECONDS; // 空值缓存时间

    // 使用ThreadLocalRandom提高并发性能
    // Random在多线程环境下性能较差，ThreadLocalRandom是专门为并发场景设计的

    /**
     * 同步带重试的读操作
     * 特点：阻塞调用线程，直到成功或彻底失败。用于需要立即获取结果的场景。
     * 示例：用户下单前查询商品信息。
     */
    public <T> CacheResult<T> getWithRetry(CacheSyncParam<T> param, int maxRetries) {
        return executeWithRetry(() -> getFromCache(param), maxRetries, param.getKey(), "read");
    }

    /**
     * 同步带重试的写操作
     * 特点：阻塞调用线程，确保缓存更新成功。用于数据库和缓存强一致的场景。
     * 示例：扣减库存后，必须同步更新缓存。
     */
    public void writeWithRetry(CacheSyncParam param, int maxRetries) {
        executeWithRetry(() -> {
            cacheResult(param);
            return null; // 适配Void方法
        }, maxRetries, param.getKey(), "write");
    }

    /**
     * 情况3：异步带重试的写操作（最大努力通知）
     * 特点：不阻塞主流程，后台尽最大努力更新。用于可接受最终一致性的场景。
     * 示例：更新用户个人头像后，缓存可以异步更新。
     */
    public CompletableFuture<Void> writeWithAsyncRetry(CacheSyncParam param, int maxRetries) {
        return CompletableFuture.runAsync(() -> {
            try {
                writeWithRetry(param, maxRetries);
            } catch (Exception e) {
                log.error("Async cache update finally failed for key: {}. Manual compensation may be needed.",
                        param.getKey(), e);
                // 此处不向外抛出异常，因为这是"最大努力"。可以记录到补偿表供后续处理。
            }
        }, retryExecutor);
    }

    /**
     * 核心重试逻辑（同步）
     */
    private <T> T executeWithRetry(Supplier<T> operation, int maxRetries, String key, String opType) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts <= maxRetries) {
            try {
                return operation.get(); // 执行实际操作
            } catch (Exception e) {
                lastException = e;
                attempts++;

                if (attempts < maxRetries && isRetryableException(e)) {
                    // 可重试的异常，等待后继续
                    log.warn("Redis {} op failed, will retry. Key: [{}], Attempt: {}/{}, Error: {}",
                            opType, key, attempts, maxRetries, e.getMessage());
                    doWaitBeforeRetry(attempts, key);
                } else {
                    // 不可重试或达到最大次数，彻底失败
                    log.error("Redis {} op finally failed after {} attempts. Key: [{}]",
                            opType, attempts, key, e);
                    throw new CacheOperationException("Redis operation failed", e);
                }
            }
        }
        // 理论上不会执行到此处
        throw new CacheOperationException("Unexpected retry logic error", lastException);
    }

    /**
     * 异常分类 - 判断哪些异常值得重试
     */
    private boolean isRetryableException(Exception e) {
        // 可重试异常：通常是暂时的、网络相关的、可自我恢复的
        return e instanceof DataAccessResourceFailureException  // 连接断开（Spring异常）
                || e instanceof RedisSystemException           // 系统级错误（Spring异常）
                || e instanceof QueryTimeoutException          // 查询超时（Spring异常）
                || e instanceof SocketTimeoutException         // Socket超时（Java标准异常）
                || (e instanceof InvalidDataAccessApiUsageException &&
                !isBusinessLogicError((InvalidDataAccessApiUsageException) e)); // 非业务逻辑的API使用错误
    }

    /**
     * 关键点2：区分不可重试的业务逻辑错误
     */
    private boolean isBusinessLogicError(InvalidDataAccessApiUsageException e) {
        String msg = e.getMessage().toLowerCase();
        Throwable cause = e.getCause();
        String causeMsg = cause != null ? cause.getMessage().toLowerCase() : "";

        // 不可重试异常：通常是永久的、代码逻辑错误，重试无意义
        return msg.contains("wrong number of arguments")    // 命令参数错误
                || msg.contains("unknown command")          // 未知命令
                || msg.contains("syntax error")             // 语法错误
                || msg.contains("wrongtype")                // 类型操作错误（如对字符串执行HASH操作）
                || causeMsg.contains("wrong number of arguments")
                || causeMsg.contains("unknown command")
                || causeMsg.contains("syntax error")
                || causeMsg.contains("wrongtype");
    }

    /**
     * 关键点3：重试等待策略（指数退避 + 随机抖动）
     */
    private void doWaitBeforeRetry(int attempt, String key) {
        try {
            long waitTime = calculateBackoffWithJitter(attempt);
            log.debug("Waiting {} ms before retry. Key: [{}]", waitTime, key);
            Thread.sleep(waitTime);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new CacheOperationException("Retry interrupted", ie);
        }
    }

    private long calculateBackoffWithJitter(int attempt) {
        long baseDelayMs = 200L; // 基础延迟
        long maxDelayMs = 5000L; // 最大延迟，避免等待过长

        // 指数退避: base * 2^(attempt-1)
        long exponentialDelay = baseDelayMs * (1L << (attempt - 1));
        long delay = Math.min(exponentialDelay, maxDelayMs);

        // 随机抖动: 添加0-1秒的随机值，避免多个客户端同时重试（惊群效应）
        long jitter = ThreadLocalRandom.current().nextLong(0, 1000);
        return delay + jitter;
    }

    /** 获取缓存 */
    public <T> CacheResult<T> getFromCache(CacheSyncParam<T> param) {
        String key = param.getKey();
        Class<T> clazz = param.getClazz();
        CacheType cacheType = param.getCacheType();
        T result = null;
        try {
            switch (cacheType) {
                case STRING:
                    result = get(key, clazz);
                    if (result == NullObject.NULL_STR_VALUE) {
                        return CacheResult.nullCache();
                    }
                    break;
                case HASH:
                    result = hGetAll(key, clazz);
                    if (result == NullObject.NULL_HASH_VALUE) {
                        return CacheResult.nullCache();
                    }
                    break;
                default:
                    log.warn("不支持的缓存类型: {}", cacheType);
                    return CacheResult.trans(null);
            }
        } catch (Exception e) {
            log.info("getFromCache fail, error: {}", e.getMessage());
            throw e;
        }
        log.debug("getFromCache success, key: {}, result: {}", key, result);
        return CacheResult.trans(result);
    }

    /** 缓存结果 */
    public void cacheResult(CacheSyncParam param) {
        String key = param.getKey();
        CacheType cacheType = param.getCacheType();
        Object result = param.getNewValue();
        int baseExpire = param.getExpireTime();
        TimeUnit timeUnit = param.getTimeUnit();
        Boolean cacheNull = param.getCacheNull();
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
                    log.warn("not support cacheType: {}", cacheType);
            }

            log.debug("cacheResult success, key: {}, expire: {}{}", key, randomExpire, timeUnit);

        } catch (Exception e) {
            log.info("cacheResult fail, error: {}", e.getMessage());
            throw e;
        }
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

            log.debug("cacheNullValue success, key: {}", key);

        } catch (Exception e) {
            log.warn("cacheNullValue fail, key: {}", key, e);
            throw e;
        }
    }

    /**
     * 获取随机过期时间（防雪崩）
     */
    private int getRandomExpire(int baseExpire) {
        // 在基础过期时间上增加随机偏移（±20%）
        int offset = (int) (baseExpire * 0.2);
        int randomOffset = ThreadLocalRandom.current().nextInt(-offset, offset + 1);
        return Math.max(1, baseExpire + randomOffset); // 确保不小于1
    }

    /**
     * 创建空实例
     */
    private Object createEmptyInstance(Class<?> clazz) {
        try {
            return instanceFactory.createInstanceSafely(clazz);
        } catch (Exception e) {
            log.warn("createEmptyInstance fail, clazz: {}", clazz.getName(), e);
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
            log.error("set value fail, key: {}", key, e);
            throw e;
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

    /**
     * 浮点数增量操作（INCRBYFLOAT）
     * 精度警告：Redis 使用 IEEE 754 双精度浮点数，存在精度误差
     * 示例：incrbyfloat key 2.2 可能返回 "23.19999999999999929" 而不是 "23.2"
     * 建议：对于金额、价格等需要精确计算的场景，使用整数存储法，将浮点数乘以倍数（如100、1000）转为整数存储
     */
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
        redisTemplate.opsForHash().putAll(key, hashMap);
        if (timeout > 0) {
            redisTemplate.expire(key, timeout, timeUnit);
        }
    }

    /**
     * HMSET 便捷方法（与 hSetAll 等价，语义更贴近 Redis 命令）
     */
    public <K, V> void hmset(String key, Map<K, V> map) {
        hSetAll(key, map);
    }

    /**
     * HMGET 便捷方法：按字段集合批量获取值
     */
    public <K, V> List<V> hmget(String key, Collection<K> fields, Class<V> clazz) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }
        // 转换为符合 Spring Data Redis 签名的字段集合类型
        List<Object> fieldList = new ArrayList<>(fields.size());
        for (K f : fields) {
            fieldList.add(f);
        }
        List<Object> values = redisTemplate.opsForHash().multiGet(key, fieldList);
        if (values == null) {
            return Collections.emptyList();
        }
        List<V> result = new ArrayList<>(values.size());
        for (Object value : values) {
            result.add(safeCast(value, clazz));
        }
        return result;
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

    public <T> Long hIncrBy(String key, T value, long delta) {
        return boundHash(key).increment(value, delta);
    }

    /**
     * Hash字段浮点数增量操作（HINCRBYFLOAT）
     * 建议：对于金额、价格等需要精确计算的场景，使用整数存储法，将浮点数乘以倍数（如100、1000）转为整数存储
     */
    public <T> Double hIncrByFloat(String key, T field, double delta) {
        return boundHash(key).increment(field, delta);
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
            log.error("del key failed, key: {}", key, e);
            return false;
        }
    }

    public Long delMulti(String... keys) {
        return redisTemplate.delete(Arrays.asList(keys));
    }

    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
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

    /* ========== 浮点数精度工具方法 ========== */

    /**
     * 使用整数存储法进行浮点数增量（推荐用于金融场景）
     * 原理：将浮点数乘以倍数转为整数存储，避免浮点数精度误差
     * 例如：金额 12.34 元，乘以 100 转为 1234 分存储
     */
    public Double incrByFloatAsInteger(String key, double delta, int scale) {
        if (scale <= 0) {
            throw new IllegalArgumentException("Scale must be greater than 0");
        }
        // 将浮点数转为整数（乘以倍数）
        long integerDelta = Math.round(delta * scale);
        // 使用整数增量命令
        Long result = incrBy(key, integerDelta);
        // 返回原始单位（除以倍数）
        return result != null ? result.doubleValue() / scale : null;
    }

    /**
     * 使用整数存储法进行 Hash 字段浮点数增量（推荐用于金融场景）
     */
    public <T> Double hIncrByFloatAsInteger(String key, T field, double delta, int scale) {
        if (scale <= 0) {
            throw new IllegalArgumentException("Scale must be greater than 0");
        }
        // 将浮点数转为整数（乘以倍数）
        long integerDelta = Math.round(delta * scale);
        // 使用整数增量命令
        Long result = hIncrBy(key, field, integerDelta);
        // 返回原始单位（除以倍数）
        return result != null ? result.doubleValue() / scale : null;
    }

    /**
     * 获取使用整数存储法的浮点数值
     */
    public Double getFloatAsInteger(String key, int scale) {
        if (scale <= 0) {
            throw new IllegalArgumentException("Scale must be greater than 0");
        }
        Long integerValue = get(key, Long.class);
        return integerValue != null ? integerValue.doubleValue() / scale : null;
    }

    /**
     * 获取 Hash 字段使用整数存储法的浮点数值
     */
    public <T> Double hGetFloatAsInteger(String key, T field, int scale) {
        if (scale <= 0) {
            throw new IllegalArgumentException("Scale must be greater than 0");
        }
        Long integerValue = hGet(key, field, Long.class);
        return integerValue != null ? integerValue.doubleValue() / scale : null;
    }
}