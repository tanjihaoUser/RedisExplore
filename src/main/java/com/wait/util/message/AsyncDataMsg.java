package com.wait.util.message;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.DataOperationType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 异步数据消息 - 用于写回策略中的异步数据库操作
 * 支持保存(SAVE)和删除(DELETE)操作，包含重试机制
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
public class AsyncDataMsg<T> implements Serializable {

    /** 操作类型：SAVE, DELETE */
    private DataOperationType type;

    /** 实体类型，如 User, Order */
    private String entityType;

    /** 缓存键 */
    private String key;

    /** 业务数据（SAVE时使用） */
    private T data;

    /** 创建时间戳 */
    private long timestamp;

    /** 重试次数 */
    private int retryCount;

    /** 构造函数 - 用于保存操作 */
    public AsyncDataMsg(DataOperationType type, CacheSyncParam<T> param) {
        this.type = type;
        this.entityType = param.getClazz().toString();
        this.key = param.getKey();
        this.data = param.getNewValue();
        this.timestamp = System.currentTimeMillis();
        this.retryCount = 0;
    }

}