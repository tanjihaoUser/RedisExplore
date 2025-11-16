package com.wait.config.script;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 获取所有lua脚本，这里定义的 getAllLuaScripts() 方法不支持返回多种返回值的脚本
 * 如果脚本返回值不一样，常见的有两种做法（假设按功能建目录，如果按返回值类型建目录，可以统一，取目录名作为返回值）
 * 1. 脚本数量较少，5 - 10个，每个目录建立一个Config，LuaScriptConfig提供了公用方法，只需要指定目录名和返回值类型即可
 * 2. 脚本数量较多，超过20个
 *      可以约定文件名命名规范，比如以类型结尾，如slide_window.long.lua，建立全局map，统一管理
 *      也可以配置到 yaml 中，包括路径和返回类型
 *  这里采用第一种方式
 * */
public abstract class LuaScriptConfig {

    /**
     * 读取目录下所有脚本，返回map，key是文件名。不支持Object父类，如long型不能转换为Object
     */
//    @Bean
//    public Map<String, DefaultRedisScript<Long>> getAllLuaScripts() throws IOException {
//        Map<String, DefaultRedisScript<Long>> luaScriptMap = new HashMap<>();
//        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
//        // classpath对应maven目录的src/main/resources
//        /*
//         * maven编译的原理如下
//         * src/main/java下源文件编译后放到target/classes(classpath根)目录下
//         * src/main/resources下文件夹直接复制到target/classes目录下/
//         * src/main/test下目录编译和复制到target/test-classes(测试classpath根)目录下
//         * target目录下还有xxx.jar，表示打包后classpath根，可以用 java - jar 命令启动
//         */
//        Resource[] resources = resolver.getResources("classpath:lua/*.lua");
//        log.info("resources size: {}, content: {}", resources.length, resources);
//        for (Resource res : resources) {
//            String name = res.getFilename(); // slide_rate.lua
//            log.info("file: {} has init", name);
//            String key = name.substring(0, name.lastIndexOf('.')); // slide_rate
//            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
//            script.setLocation(res);
//            script.setResultType(Long.class);
//            luaScriptMap.put(key, script);
//        }
//        log.info("map: {}", luaScriptMap);
//        return luaScriptMap;
//    }

    public <T> DefaultRedisScript<T> createScript(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(resultType);
        return script;
    }
}
