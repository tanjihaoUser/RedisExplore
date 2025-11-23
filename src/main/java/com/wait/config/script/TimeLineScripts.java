package com.wait.config.script;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 时间线操作脚本管理类
 * 管理发布、删除帖子等操作的 Lua 脚本
 */
@Component
@Slf4j
public class TimeLineScripts extends LuaScriptConfig {

    public static final String PUBLISH_POST = "publish_post";
    public static final String DELETE_POST = "delete_post";

    public TimeLineScripts(StringRedisTemplate stringRedisTemplate) {
        super(stringRedisTemplate);
    }

    @Override
    protected Map<String, Class<?>> buildReturnTypeMap() {
        Map<String, Class<?>> returnTypeMap = new HashMap<>();
        returnTypeMap.put(PUBLISH_POST, Long.class);
        returnTypeMap.put(DELETE_POST, Long.class);
        return Collections.unmodifiableMap(returnTypeMap);
    }

    @Override
    protected String getScriptDirectory() {
        return "classpath:lua/timeline/*.lua";
    }
}
