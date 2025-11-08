package com.wait.entity.type;

/**
 * 缓存状态枚举
 */
public enum CacheStatus {
    /** 命中有效值 */
    HIT,
    /** 命中空值标记 */
    NULL_CACHE,
    /** 未命中 */
    MISS
}
