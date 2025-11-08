package com.wait.service.impl;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.wait.util.BoundUtil;
import com.wait.util.limit.RateLimiter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitServiceImpl {

    @Qualifier("slideWindow")
    private final RateLimiter rateLimiter;

    private final BoundUtil boundUtil;

    public <T> T getByKey(String key, Class<T> clazz) {
        return boundUtil.get(key, clazz);
    }

    public int getWithLimit(String key, int limit, int interval, TimeUnit unit) {
        boolean allowed = rateLimiter.allowRequest(key, limit, interval, unit);
        if (allowed) {
            int res = boundUtil.get(key, Integer.class);
            log.info("get key success, key: {}, value: {}", key, res);
            return res;
        }
        // 被限流时返回默认值
        return 100;
    }
}
