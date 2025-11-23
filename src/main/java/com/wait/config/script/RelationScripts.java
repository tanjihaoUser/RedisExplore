package com.wait.config.script;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 关系操作脚本管理类
 * 管理关注、点赞、收藏、黑名单等操作的 Lua 脚本
 */
@Component
@Slf4j
public class RelationScripts extends LuaScriptConfig {

    public static final String FOLLOW = "follow";
    public static final String UNFOLLOW = "unfollow";
    public static final String LIKE_POST = "like_post";
    public static final String UNLIKE_POST = "unlike_post";
    public static final String FAVORITE_POST = "favorite_post";
    public static final String UNFAVORITE_POST = "unfavorite_post";
    public static final String BLOCK_USER = "block_user";
    public static final String UNBLOCK_USER = "unblock_user";

    public RelationScripts(StringRedisTemplate stringRedisTemplate) {
        super(stringRedisTemplate);
    }

    @Override
    protected Map<String, Class<?>> buildReturnTypeMap() {
        Map<String, Class<?>> returnTypeMap = new HashMap<>();
        returnTypeMap.put(FOLLOW, Long.class);
        returnTypeMap.put(UNFOLLOW, Long.class);
        returnTypeMap.put(LIKE_POST, Long.class);
        returnTypeMap.put(UNLIKE_POST, Long.class);
        returnTypeMap.put(FAVORITE_POST, Long.class);
        returnTypeMap.put(UNFAVORITE_POST, Long.class);
        returnTypeMap.put(BLOCK_USER, Long.class);
        returnTypeMap.put(UNBLOCK_USER, Long.class);
        return Collections.unmodifiableMap(returnTypeMap);
    }

    @Override
    protected String getScriptDirectory() {
        return "classpath:lua/relation/*.lua";
    }

}

