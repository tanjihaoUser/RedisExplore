package com.wait.util.limit;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import com.wait.config.script.RateLimitScripts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.wait.util.BoundUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 令牌桶实现限流
 * 第一次可能出现瞬时并发成功的情况，因为桶的初始容量不为0。后续请求可以实现均匀处理流量，实现平滑过滤
 * 这里脚本简化了，初始令牌数量和桶容量用一个参数，不能简单设置参数为0，这样桶容量也是0了
 * 可以增加一个参数，区分初始令牌数量和桶容量
 */
@Component
@Slf4j
public class TokenBucket extends RateLimiter {

    private final DefaultRedisScript<Long> tokenBucketScript;

    public TokenBucket(BoundUtil boundUtil,
            @Qualifier(RateLimitScripts.TOKEN_BUCKET) DefaultRedisScript<Long> tokenBucketScript) {
        super(boundUtil);
        this.tokenBucketScript = tokenBucketScript;
    }

    @Override
    public Long tryAcquire(String key, int limit, int windowSize, TimeUnit unit) {
        String rateKey = LIMIT_STR + key;
        long current = System.currentTimeMillis();

        // 精确计算速率（令牌/秒）
        long windowSeconds = unit.toSeconds(windowSize);
        long rate = (windowSeconds > 0) ? limit / windowSeconds : limit;
        rate = Math.max(1, rate); // 确保至少为1

        long burst = limit; // 桶容量等于限制数

        Long allowed = boundUtil.executeSpecificScript(
                tokenBucketScript,
                Arrays.asList(rateKey),
                rate, burst, current);

        if (allowed == null) {
            log.error("execute fail, key: {}", key);
            return -1L;
        } else if (allowed == 1L) {
            String formattedDate = ZonedDateTime.now().format(formatter);
            log.info("request allow, time: {}", formattedDate);
        }

        return allowed; // 1=允许, 0=限流
    }

}
