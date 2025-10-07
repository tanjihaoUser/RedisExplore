package com.wait.util.limit;

import com.wait.util.LuaScriptConfig;
import com.wait.util.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 滑动窗口实现限流，调用lua脚本。
 * 特点：窗口初期释放所有容量，可能瞬间卖完
 * */
@Component
public class SlideWindow extends RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SlideWindow.class);

    private static final AtomicLong COUNTER = new AtomicLong(0); // 静态计数器

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
            log.error("限流脚本执行失败, key: {}", key);
            return -1L;
        }

        if (spare == -1) {
            rateKey = "";
//            log.info("请求被限流, key: {}, 时间窗口: {}{}", key, windowSize, unit);
        } else {
            String formattedDate = ZonedDateTime.now().format(formatter);
            log.info("请求允许, key: {}, 剩余次数: {}, time: {}", key, spare, formattedDate);
        }

        return spare;
    }

    /**
     * 生成唯一成员标识
     */
    private String generateUniqueMember() {
        // 使用更精确的纳秒时间 + 原子长计数器 + 实例标识
        return String.format("%d-%d-%d-%d",
                System.currentTimeMillis(),
                System.nanoTime() % 1000000,  // 纳秒部分
                COUNTER.incrementAndGet(),    // 移除取模，使用长整型最大值
                Thread.currentThread().getId() // 加上线程ID
        );
    }
}
