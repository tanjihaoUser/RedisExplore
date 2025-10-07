package com.wait.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class LuaScriptConfig {

    private static final Logger log = LoggerFactory.getLogger(LuaScriptConfig.class);

    public static final String SLIDE_WINDOW = "slide_window";
    public static final String TOKEN_BUCKET = "token_bucket";

    /**
     * 读取目录下所有脚本，返回map，key是文件名。不支持Object父类，如long型不能转换为Object
     * */
    @Bean
    public Map<String, DefaultRedisScript<Long>> getAllLuaScripts() throws IOException {
        Map<String, DefaultRedisScript<Long>> luaScriptMap = new HashMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        // classpath对应maven目录的src/main/resources
        /*
         * maven编译的原理如下
         *  src/main/java下源文件编译后放到target/classes(classpath根)目录下
         *  src/main/resources下文件夹直接复制到target/classes目录下/
         *  src/main/test下目录编译和复制到target/test-classes(测试classpath根)目录下
         *  target目录下还有xxx.jar，表示打包后classpath根，可以用 java - jar 命令启动
         * */
        Resource[] resources = resolver.getResources("classpath:lua/*.lua");
        log.info("resources size: {}, content: {}", resources.length, resources);
        for (Resource res : resources) {
            String name = res.getFilename();          // slide_rate.lua
            log.info("file: {} has init", name);
            String key  = name.substring(0, name.lastIndexOf('.')); // slide_rate
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setLocation(res);
            script.setResultType(Long.class);
            luaScriptMap.put(key, script);
        }
        log.info("map: {}", luaScriptMap);
        return luaScriptMap;
    }

    /**
     * 读取单个脚本，并注入实例对象
     * */
//    @Bean("slideWindowScript")
//    public DefaultRedisScript<Long> slideWindowScript() {
//        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
//        script.setLocation(new ClassPathResource("lua/slide_window.lua"));
//        script.setResultType(Long.class);
//        return script;
//    }
}
