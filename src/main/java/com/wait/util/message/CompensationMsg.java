package com.wait.util.message;

import com.wait.entity.CacheSyncParam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 补偿消息 - 用于记录失败的操作，便于后续重试或人工处理
 * @param <T> 消息数据类型
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompensationMsg<T> {

    /** 原始缓存同步参数 */
    private CacheSyncParam<T> originalParam;
    
    /** 失败原因 */
    private String failReason;
    
    /** 失败时间戳 */
    private long failTime;
}