package com.wait.entity.type;

/**
 * 写策略类型
 */
public enum WriteStrategyType {
    CACHE_ASIDE,        // 先DB后缓存（默认）
    WRITE_THROUGH,      // 写穿透（先缓存后DB）
    WRITE_BEHIND,       // 写回（异步更新DB）
}
