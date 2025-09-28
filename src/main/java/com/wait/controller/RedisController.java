package com.wait.controller;

import com.wait.service.RedisServiceImpl;
import com.wait.util.RateLimiter;
import com.wait.util.BoundUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/redis")
public class RedisController {

    @Autowired
    private RedisServiceImpl redisService;

    private int DEFAULT_LIMIT = 10;
    private int DEFAULT_INTERVAL = 1;
    private TimeUnit DEFAULT_TIMEUNIT = TimeUnit.SECONDS;

    @PostMapping("/ratelimit")
    public Object getKeyWithRateLimit(@RequestParam("key") String key) {
        int value = redisService.getWithLimit(key, DEFAULT_LIMIT, DEFAULT_INTERVAL, DEFAULT_TIMEUNIT);
        return value;
    }

}
