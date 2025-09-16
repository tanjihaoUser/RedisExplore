package com.wait.service;

import com.wait.util.BoundUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    @Autowired
    private BoundUtil boundUtil;

    private static final String LIMIT_STR = ":limit";

    public void setLimit(String key, int max) {
        boundUtil.set(key + LIMIT_STR, max + "");
    }

    public Object getContent(String key) {
        String s = boundUtil.get(key + LIMIT_STR);
        if (s == null || s.isEmpty()) {
            Object value = boundUtil.get(key);
            log.info("key: {} not has limit rate, value: {}", key, value);
            return value;
        }
        long time = System.currentTimeMillis() / 1000;
        String spareStr = boundUtil.get(key + time);
        int spare = spareStr == null ? Integer.parseInt(s) : Integer.parseInt(spareStr);
        if (spare <= 0) {
            log.info("visit limit, can not get value");
            return null;
        } else if (spareStr == null) {
            log.info("first visit, init rate limit: {}", spare);
        }
        spare -= 1;
        boundUtil.setEx(key + time, spare + "", Duration.ofSeconds(1));
        String value = boundUtil.get(key);
        log.info("can access, key: {}, value: {}", key, value);
        return value;
    }
}
