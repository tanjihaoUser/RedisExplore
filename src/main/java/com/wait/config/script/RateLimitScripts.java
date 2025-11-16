package com.wait.config.script;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RateLimitScripts extends LuaScriptConfig {

    public static final String FILE_PATH = "lua/rate_limit/%s.lua";
    public static final String SLIDE_WINDOW = "slide_window";
    public static final String TOKEN_BUCKET = "token_bucket";

    @Bean(SLIDE_WINDOW)
    public DefaultRedisScript<Long> slideWindow() {
        return createScript(String.format(FILE_PATH, SLIDE_WINDOW), Long.class);
    }

    @Bean(TOKEN_BUCKET)
    public DefaultRedisScript<Long> tokenBucket() {
        return createScript(String.format(FILE_PATH, TOKEN_BUCKET), Long.class);
    }

}
