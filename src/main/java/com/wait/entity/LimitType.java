package com.wait.entity;

/**
 * 限流器类型枚举
 */
public enum LimitType {
    SLIDE_WINDOW,    // 滑动窗口
    TOKEN_BUCKET,    // 令牌桶
    LEAKY_BUCKET     // 漏桶
}
