package com.wait.controller;

import com.wait.annotation.RateLimit;
import com.wait.config.LimitType;
import com.wait.service.RedisServiceImpl;
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

    @PostMapping("/rateSlideWindow")
    // 引用静态常量必须这么写，不能前面import，这里使用#key的格式，或者将前缀拼接放到切面中
    @RateLimit(
            key = "T(com.wait.util.RateLimiter).LIMIT_STR + #key",
            window = 1,
            unit = TimeUnit.SECONDS,
            limit = 10,
            type = LimitType.SLIDE_WINDOW
    )
    public Object getKeyWithSlideWindow(@RequestParam("key") String key) {
        int value = redisService.getByKey(key, Integer.class);
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
        int value = redisService.getByKey(key, Integer.class);
        return value;
    }

}
