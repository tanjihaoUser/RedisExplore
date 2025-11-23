package com.wait.config.script;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import lombok.extern.slf4j.Slf4j;

/**
 * Lua脚本配置基类
 * 获取所有lua脚本，这里定义的 getAllLuaScripts() 方法不支持返回多种返回值的脚本
 * 如果脚本返回值不一样，常见的有两种做法（假设按功能建目录，如果按返回值类型建目录，可以统一，取目录名作为返回值）
 * 1. 脚本数量较少，5 - 10个，每个目录建立一个Config，LuaScriptConfig提供了公用方法，只需要指定目录名和返回值类型即可
 * 2. 脚本数量较多，超过20个
 *      可以约定文件名命名规范，比如以类型结尾，如slide_window.long.lua，建立全局map，统一管理
 *      也可以配置到 yaml 中，包括路径和返回类型
 *  这里采用第一种方式
 */
@Slf4j
public abstract class LuaScriptConfig {

    protected final StringRedisTemplate stringRedisTemplate;

    private volatile boolean initialized = false;
    private final Object initializationMonitor = new Object();
    private Map<String, DefaultRedisScript<?>> cachedScripts = Collections.emptyMap();
    private Map<String, Class<?>> cachedReturnTypes = Collections.emptyMap();

    protected LuaScriptConfig(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 初始化脚本（子类可覆盖，默认在PostConstruct时调用）
     */
    @PostConstruct
    public void init() {
        ensureInitialized();
    }

    /**
     * 执行脚本
     */
    public <T> T executeScript(String scriptName, List<String> keys, Object... args) {
        ensureInitialized();
        DefaultRedisScript<?> script = cachedScripts.get(scriptName);
        if (script == null) {
            throw new IllegalArgumentException(
                    "lua script not found: " + scriptName + ", existing scripts: " + cachedScripts.keySet());
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

    /**
     * 确保脚本已初始化（双重检查锁定）
     */
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

    /**
     * 构建脚本名称到返回类型的映射
     * 由于大部分脚本返回值是 long 型，因此使用 long 类型作为默认返回类型
     * 对于非 long 类型的脚本，需要子类覆盖此方法返回正确的返回类型
     */
    protected abstract Map<String, Class<?>> buildReturnTypeMap();

    /**
     * 获取脚本目录路径（相对于classpath）
     * 子类必须实现此方法，返回脚本所在的目录路径
     * 例如："classpath:lua/relation/*.lua"
     * classpath对应maven目录的src/main/resources
     * maven编译的原理如下
     * src/main/java下源文件编译后放到target/classes(classpath根)目录下
     * src/main/resources下文件夹直接复制到target/classes目录下/
     * src/main/test下目录编译和复制到target/test-classes(测试classpath根)目录下
     * target目录下还有xxx.jar，表示打包后classpath根，可以用 java - jar 命令启动
     */
    protected abstract String getScriptDirectory();

    /**
     * 获取脚本分类名称（用于日志）
     * 子类可以覆盖此方法，返回自定义的分类名称
     */
    protected String getScriptCategoryName() {
        String directory = getScriptDirectory();
        int begin = directory.indexOf('/'), end = directory.lastIndexOf('/');
        if (begin != -1 && end != -1) {
            return directory.substring(begin + 1, end);
        }
        return "scripts";
    }

    /**
     * 加载脚本
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map<String, DefaultRedisScript<?>> loadScripts(Map<String, Class<?>> returnTypeMap) {
        Map<String, DefaultRedisScript<?>> luaScriptMap = new HashMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            String scriptDirectory = getScriptDirectory();
            Resource[] resources = resolver.getResources(scriptDirectory);
            log.info("Loading {} scripts, resources size: {}", getScriptCategoryName(), resources.length);

            for (Resource res : resources) {
                String name = res.getFilename();
                if (name == null || !name.contains(".")) {
                    log.warn("file name is null or not contains '.': {}", name);
                    continue;
                }
                String key = name.substring(0, name.lastIndexOf('.'));
                log.info("Loading script: {}", key);

                DefaultRedisScript script = new DefaultRedisScript<>();
                script.setLocation(res);
                Class<?> returnType = returnTypeMap.get(key);
                if (returnType == null) {
                    log.warn("not found return type for script: {},  default set Long", key);
                    returnType = Long.class;
                }
                script.setResultType(returnType);
                luaScriptMap.put(key, script);
            }
        } catch (IOException e) {
            throw new IllegalStateException("load " + getScriptCategoryName() + " lua scripts failed", e);
        }
        // 返回不可修改的映射
        Map<String, DefaultRedisScript<?>> unmodifiable = Collections.unmodifiableMap(luaScriptMap);
        log.info("Loaded {} scripts: {}", getScriptCategoryName(), unmodifiable.keySet());
        return unmodifiable;
    }

    /**
     * 创建单个脚本
     */
    public <T> DefaultRedisScript<T> createScript(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(resultType);
        return script;
    }
}
