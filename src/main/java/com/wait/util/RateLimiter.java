package com.wait.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final AtomicLong COUNTER = new AtomicLong(0); // 静态计数器

    @Autowired
    private BoundUtil boundUtil;

    public static final String LIMIT_STR = "limit:";

    /**
     * 简要写法，存在一些问题
     *  临界点二倍突刺、前几次崩溃导致key永久、无法确保原子性
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

    public Long tryAcquire(String key, int limit, int windowSize, TimeUnit unit) {
        String rateKey = LIMIT_STR + key;
        long current = System.currentTimeMillis();
        long windowMillis = unit.toMillis(windowSize);
        String id = generateUniqueMember();

        Long spare = boundUtil.executeScriptByMap(
                LuaScriptConfig.SLIDE_WINDOW,
                Arrays.asList(rateKey),
                limit, windowMillis, current, id);

        if (spare == null) {
            log.error("限流脚本执行失败, key: {}", key);
            return -1L;
        }

        if (spare == -1) {
            log.info("请求被限流, key: {}, 时间窗口: {}{}", key, windowSize, unit);
        } else {
            log.debug("请求允许, key: {}, 剩余次数: {}", key, spare);
        }

        return spare;
    }

    /**
     * 生成唯一成员标识
     */
    private String generateUniqueMember() {
        return String.format("%d-%d-%d",
                System.currentTimeMillis(),
                COUNTER.incrementAndGet() % 10000,
                ThreadLocalRandom.current().nextInt(1000)
        );
    }

    /**
     * 检查是否允许访问（便捷方法）
     */
    public boolean allowRequest(String key, int limit, int windowSize, TimeUnit unit) {
        long remaining = tryAcquire(key, limit, windowSize, unit);
        return remaining >= 0;
    }
}
