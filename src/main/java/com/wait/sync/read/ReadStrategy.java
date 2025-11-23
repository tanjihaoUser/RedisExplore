package com.wait.sync.read;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.ReadStrategyType;
import com.wait.sync.MethodExecutor;

// 读写分离设计
public interface ReadStrategy {
    <T> T read(CacheSyncParam<T> param, MethodExecutor methodExecutor);

    ReadStrategyType getStrategyType();
}
