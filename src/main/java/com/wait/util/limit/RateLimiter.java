package com.wait.util.limit;

import com.wait.util.BoundUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 抽象类，脚本默认返回long型值，1表示允许访问
 * */
@Slf4j
public abstract class RateLimiter {

    @Autowired
    protected BoundUtil boundUtil;

    public static final String LIMIT_STR = "limit:";

    public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS Z");

    /**
     * 简要写法，存在一些问题
     * 临界点二倍突刺、前几次崩溃导致key永久、无法确保原子性
     * */
    public Object getContent(String key, int max, int windowSize, TimeUnit unit) {

        long unitMillis = unit.toMillis(windowSize);
        long unitStart = System.currentTimeMillis() / unitMillis;
        String rateKey = LIMIT_STR + key + ":" + unitStart;

        // 简要写法，不确保原子性
        Long count = boundUtil.incr(rateKey);
        // 防止Redis前三次请求崩溃，key变成永久
        if (count <= 3) {
            boundUtil.expire(rateKey, unit.toSeconds(1), TimeUnit.SECONDS);
        }
        Object value = null;
        if (count <= max) {
            value = boundUtil.get(key, String.class);
            log.info("can access, key: {}, value: {}", key, value);
        } else {
            log.info("visit limited, return null, key: {}", key);
        }
        String s = boundUtil.get(key + LIMIT_STR, String.class);
        return value;
    }

    /**
     * 检查是否允许访问（便捷方法）
     * 这里lua脚本返回值都是number类型，1表示允许访问，0表示不允许访问
     */
    public boolean allowRequest(String key, int limit, int windowSize, TimeUnit unit) {
        long remaining = tryAcquire(key, limit, windowSize, unit);
        return remaining > 0L;
    }

    /**
     * 限流校验
     * */
    public abstract Long tryAcquire(String key, int limit, int windowSize, TimeUnit unit);

}
