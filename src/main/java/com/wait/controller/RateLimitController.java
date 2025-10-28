package com.wait.controller;

import com.wait.annotation.RateLimit;
import com.wait.entity.type.LimitType;
import com.wait.service.impl.RateLimitServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/redis")
public class RateLimitController {

    @Autowired
    private RateLimitServiceImpl rateLimitService;

    private int DEFAULT_LIMIT = 10;
    private int DEFAULT_INTERVAL = 1;
    private TimeUnit DEFAULT_TIMEUNIT = TimeUnit.SECONDS;

    @PostMapping("/ratelimit")
    public Object getKeyWithRateLimit(@RequestParam("key") String key) {
        int value = rateLimitService.getWithLimit(key, DEFAULT_LIMIT, DEFAULT_INTERVAL, DEFAULT_TIMEUNIT);
        return value;
    }

    @PostMapping("/rateSlideWindow")
    @RateLimit(
            key = "#key",
            window = 1,
            unit = TimeUnit.SECONDS,
            limit = 10,
            type = LimitType.SLIDE_WINDOW
    )
    public Object getKeyWithSlideWindow(@RequestParam("key") String key) {
        int value = rateLimitService.getByKey(key, Integer.class);
        return value;
    }

    @PostMapping("/rateTokenBucket")
    @RateLimit(
            key = "#key",
            window = 1,
            unit = TimeUnit.SECONDS,
            limit = 10,
            type = LimitType.TOKEN_BUCKET
    )
    public Object getKeyWithTokenBucket(@RequestParam("key") String key) {
        int value = rateLimitService.getByKey(key, Integer.class);
        return value;
    }

}
