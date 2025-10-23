package com.wait.sync.read;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.ReadStrategyType;
import org.aspectj.lang.ProceedingJoinPoint;

// 读写分离设计
public interface ReadStrategy {
    <T> T read(CacheSyncParam<T> param, ProceedingJoinPoint joinPoint);

    ReadStrategyType getStrategyType();
}
