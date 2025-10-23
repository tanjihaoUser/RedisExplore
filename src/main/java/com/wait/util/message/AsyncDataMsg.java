package com.wait.util.message;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.DataOperationType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 增强的写回任务实体，支持存储数据库操作
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