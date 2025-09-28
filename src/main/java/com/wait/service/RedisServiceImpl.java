package com.wait.service;

import com.wait.util.BoundUtil;
import com.wait.util.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.wait.util.RateLimiter.LIMIT_STR;

@Component
public class RedisServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(RedisServiceImpl.class);
    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private BoundUtil boundUtil;

    public String getByKey(String key) {
        return boundUtil.get(key, String.class);
    }

    public int getWithLimit(String key, int limit, int interval, TimeUnit unit) {
        boolean allowed = rateLimiter.allowRequest(key, limit, interval, unit);
        if (allowed) {
            int res = boundUtil.get(key, Integer.class);
            log.info("get key success, key: {}, value: {}", key, res);
            String rateKey = LIMIT_STR + key;
            Set<String> set = boundUtil.zRange(rateKey, 0, System.currentTimeMillis(), String.class);
            log.info("key: {}, value: {}", rateKey, set);
            return res;
        }
        log.info("visit at limit rate, return null");
        return 0;
    }
}
