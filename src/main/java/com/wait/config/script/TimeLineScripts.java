package com.wait.config.script;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class TimeLineScripts extends LuaScriptConfig {

    public static final String PUBLISH_POST = "publish_post";
    public static final String DELETE_POST = "delete_post";

    private final StringRedisTemplate stringRedisTemplate;

    private volatile boolean initialized = false;
    private final Object initializationMonitor = new Object();
    private Map<String, DefaultRedisScript<?>> cachedScripts = Collections.emptyMap();
    private Map<String, Class<?>> cachedReturnTypes = Collections.emptyMap();

    @PostConstruct
    public void init() {
        ensureInitialized();
    }

    public <T> T executeScript(String scriptName, List<String> keys, Object... args) {
        ensureInitialized();
        DefaultRedisScript<?> script = cachedScripts.get(scriptName);
        if (script == null) {
            throw new IllegalArgumentException(
                    "lua script not found: " + scriptName + "existing scripts: " + cachedScripts.keySet());
        }
        Class<?> targetType = cachedReturnTypes.get(scriptName);
        if (targetType == null) {
            throw new IllegalArgumentException("return type not found for script: " + scriptName);
        }

        // 将参数转换为字符串数组，避免 RedisTemplate 对 String 类型再次序列化
        // 业界常见做法：对于 Lua 脚本，如果参数已经序列化为字符串，使用 StringRedisTemplate 执行
        // 这样可以避免多重转义问题
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                stringArgs[i] = null;
            } else if (args[i] instanceof String) {
                // String 类型直接使用，不再序列化
                stringArgs[i] = (String) args[i];
            } else {
                // 其他类型转换为字符串
                stringArgs[i] = String.valueOf(args[i]);
            }
        }

        // 使用 StringRedisTemplate 执行脚本，避免对字符串参数再次序列化
        @SuppressWarnings("unchecked")
        T result = (T) stringRedisTemplate.execute(script, keys, (Object[]) stringArgs);

        // 类型安全检查
        if (result != null && !targetType.isInstance(result)) {
            throw new ClassCastException(String.format(
                    "script: %s return type mismatch, expected: %s, actual: %s",
                    scriptName, targetType.getName(), result.getClass().getName()));
        }

        return result;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (initializationMonitor) {
            if (initialized) {
                return;
            }
            Map<String, Class<?>> returnTypeMap = buildReturnTypeMap();
            this.cachedReturnTypes = returnTypeMap;
            this.cachedScripts = loadScripts(returnTypeMap);
            this.initialized = true;
        }
    }

    private Map<String, Class<?>> buildReturnTypeMap() {
        Map<String, Class<?>> returnTypeMap = new HashMap<>();
        returnTypeMap.put(PUBLISH_POST, Long.class);
        returnTypeMap.put(DELETE_POST, Long.class);
        return Collections.unmodifiableMap(returnTypeMap);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map<String, DefaultRedisScript<?>> loadScripts(Map<String, Class<?>> returnTypeMap) {
        Map<String, DefaultRedisScript<?>> luaScriptMap = new HashMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath:lua/timeline/*.lua");
            log.info("resources size: {}, content: {}", resources.length, resources);
            for (Resource res : resources) {
                String name = res.getFilename();
                if (name == null || !name.contains(".")) {
                    log.warn("file name is null or not contains '.': {}", name);
                    continue;
                }
                log.info("file: {} has init", name);
                String key = name.substring(0, name.lastIndexOf('.'));
                DefaultRedisScript script = new DefaultRedisScript<>();
                script.setLocation(res);
                Class<?> returnType = returnTypeMap.get(key);
                if (returnType == null) {
                    throw new IllegalStateException("not found return type for script: " + key);
                }
                script.setResultType(returnType);
                luaScriptMap.put(key, script);
            }
        } catch (IOException e) {
            throw new IllegalStateException("load lua script failed", e);
        }
        Map<String, DefaultRedisScript<?>> unmodifiable = Collections.unmodifiableMap(luaScriptMap);
        log.info("map: {}", unmodifiable);
        return unmodifiable;
    }

}
