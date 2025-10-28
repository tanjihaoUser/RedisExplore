package com.wait.service.impl;

import com.wait.util.BoundUtil;
import com.wait.util.limit.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.wait.util.limit.RateLimiter.LIMIT_STR;

@Slf4j
@Component
public class RateLimitServiceImpl {

    @Autowired
    @Qualifier("slideWindow")
//    @Qualifier("tokenBucket")
    private RateLimiter rateLimiter;

    @Autowired
    private BoundUtil boundUtil;

    public <T> T getByKey(String key, Class<T> clazz) {
        return boundUtil.get(key, clazz);
    }

    public int getWithLimit(String key, int limit, int interval, TimeUnit unit) {
        boolean allowed = rateLimiter.allowRequest(key, limit, interval, unit);
        if (allowed) {
            int res = boundUtil.get(key, Integer.class);
            log.info("get key success, key: {}, value: {}", key, res);
            String rateKey = LIMIT_STR + key;
            Set<String> set = boundUtil.zRange(rateKey, 0, System.currentTimeMillis(), String.class);
//            log.info("key: {}, value: {}", rateKey, set);
//            Map<String, Object> bucket = boundUtil.hGetAll(rateKey, String.class, Object.class);
//            log.info("bucket: {}", bucket);
            return res;
        }
//        log.info("visit at limit rate, return null");
        return 100;
    }
}
