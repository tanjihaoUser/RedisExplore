package com.wait.annotation;

import com.wait.entity.CacheType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisCache {

    /** 监控名称 */
    String name();

    /** 缓存前缀 */
    String prefix() default "";

    /** 缓存key */
    String key();

    /** 缓存过期时间 */
    int expire() default 60;

    /** 缓存时间单位 */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /** 是否缓存空值 */
    boolean isCacheNull() default true;

    /** 缓存类型 */
    CacheType cacheType() default CacheType.STRING;

    /** 缓存返回实例对象类型 */
    Class<?> returnType() default String.class;

    /** 条件表达式（SpEL），只有满足条件时才缓存 */
    String condition() default "";

    /** 排除条件（SpEL），当条件满足时不缓存（与condition相反）*/
    String unless() default "";
}
