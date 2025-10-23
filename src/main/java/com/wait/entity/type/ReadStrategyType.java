package com.wait.entity.type;

/**
 * 读策略类型
 */
public enum ReadStrategyType {
    LAZY_LOAD,           // 懒加载（默认）
    SCHEDULED_REFRESH,   // 定时刷新
    CACHE_ONLY,          // 只读缓存（缓存不存在则报错）
}
