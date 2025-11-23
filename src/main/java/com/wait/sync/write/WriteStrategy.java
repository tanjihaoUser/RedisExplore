package com.wait.sync.write;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.WriteStrategyType;
import com.wait.sync.MethodExecutor;

public interface WriteStrategy {
    void write(CacheSyncParam<?> param, MethodExecutor methodExecutor);

    void delete(CacheSyncParam<?> param, MethodExecutor methodExecutor);

    WriteStrategyType getStrategyType();
}
