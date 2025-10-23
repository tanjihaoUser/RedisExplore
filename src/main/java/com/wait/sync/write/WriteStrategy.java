package com.wait.sync.write;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.WriteStrategyType;
import org.aspectj.lang.ProceedingJoinPoint;


public interface WriteStrategy {
    void write(CacheSyncParam param, ProceedingJoinPoint joinPoint);

    void delete(CacheSyncParam param, ProceedingJoinPoint joinPoint);

    WriteStrategyType getStrategyType();
}
