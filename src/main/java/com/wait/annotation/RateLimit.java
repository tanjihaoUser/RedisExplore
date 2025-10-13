package com.wait.annotation;

import com.wait.entity.LimitType;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 限流的key，支持SpEL表达式，解析的时候加上默认前缀 */
    String key() default "";

    /** 时间窗口内允许的请求数 */
    int limit() default 100;

    /** 时间窗口大小 */
    int window() default 1;

    /** 时间单位 */
    TimeUnit unit() default TimeUnit.SECONDS;

    /** 被限流时的错误消息 */
    String message() default "request rejected";

    /** 限流器类型 */
    LimitType type() default LimitType.SLIDE_WINDOW;

    /** 是否启用限流 */
    boolean enabled() default true;
}