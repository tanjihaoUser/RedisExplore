package com.wait.entity.type;

/**
 * 写策略类型
 */
public enum WriteStrategyType {
    CACHE_ASIDE,                    // 先DB后缓存（默认）
    WRITE_THROUGH,                  // 写穿透（先缓存后DB）
    WRITE_BEHIND_MQ,                // 写回（MQ异步更新DB）
    INCREMENTAL_WRITE_BEHIND,       // 增量写回（定时更新DB）
    SNAPSHOT_WRITE_BEHIND,          // 快照写回（定时更新DB）
}
