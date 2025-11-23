package com.wait.config.script;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RateLimitScripts extends LuaScriptConfig {

    public static final String FILE_PATH = "lua/rate_limit/%s.lua";
    public static final String SLIDE_WINDOW = "slide_window";
    public static final String TOKEN_BUCKET = "token_bucket";
    public static final String LEAKY_BUCKET = "leaky_bucket";

    public RateLimitScripts(StringRedisTemplate stringRedisTemplate) {
        super(stringRedisTemplate);
    }

    @Override
    protected Map<String, Class<?>> buildReturnTypeMap() {
        Map<String, Class<?>> returnTypeMap = new HashMap<>();
        returnTypeMap.put(SLIDE_WINDOW, Long.class);
        returnTypeMap.put(TOKEN_BUCKET, Long.class);
        returnTypeMap.put(LEAKY_BUCKET, Long.class);
        return Collections.unmodifiableMap(returnTypeMap);
    }

    @Override
    protected String getScriptDirectory() {
        return "classpath:lua/rate_limit/*.lua";
    }

    /**
     * 兼容旧版本的 @Bean 方式，如果其他地方通过 @Bean 方式注入，可以保留这些方法
     */
    @Bean(SLIDE_WINDOW)
    public DefaultRedisScript<Long> slideWindow() {
        return createScript(String.format(FILE_PATH, SLIDE_WINDOW), Long.class);
    }

    @Bean(TOKEN_BUCKET)
    public DefaultRedisScript<Long> tokenBucket() {
        return createScript(String.format(FILE_PATH, TOKEN_BUCKET), Long.class);
    }
}
