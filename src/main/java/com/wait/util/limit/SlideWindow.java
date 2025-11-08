package com.wait.util.limit;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.wait.config.LuaScriptConfig;
import com.wait.util.BoundUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 滑动窗口实现限流，调用lua脚本。
 * 特点：窗口初期释放所有容量，可能瞬间卖完
 */
@Component
@Slf4j
public class SlideWindow extends RateLimiter {

    private static final AtomicLong COUNTER = new AtomicLong(0); // 静态计数器

    public SlideWindow(BoundUtil boundUtil) {
        super(boundUtil);
    }

    @Override
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
            log.error("execute fail, key: {}", key);
            return -1L;
        }

        if (spare == -1) {
            // 请求被限流
            log.debug("Request rate limited, key: {}, time window: {}{}", key, windowSize, unit);
        } else {
            String formattedDate = ZonedDateTime.now().format(formatter);
            log.info("request allow, key: {}, spare times: {}, time: {}", key, spare, formattedDate);
        }

        return spare;
    }

    /**
     * 生成唯一成员标识
     */
    private String generateUniqueMember() {
        // 使用毫秒时间 + 纳秒时间戳的低6位 + 原子长计数器 + 线程ID
        return String.format("%d-%d-%d-%d",
                System.currentTimeMillis(),
                System.nanoTime() % 1000000, // 纳秒部分的低6位，提供微秒级精度
                COUNTER.incrementAndGet(), // 原子递增计数器，确保唯一性
                Thread.currentThread().getId() // 线程ID，增加并发场景下的唯一性
        );
    }
}
