package com.wait.sync;

import com.wait.entity.type.ReadStrategyType;
import com.wait.entity.type.WriteStrategyType;
import com.wait.sync.read.ReadStrategy;
import com.wait.sync.write.WriteStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CacheStrategyFactory {

    private final Map<ReadStrategyType, ReadStrategy> readStrategies;
    private final Map<WriteStrategyType, WriteStrategy> writeStrategies;

    @Autowired
    public CacheStrategyFactory(List<ReadStrategy> readStrategyList,
                                List<WriteStrategy> writeStrategyList) {
        this.readStrategies = readStrategyList.stream()
                .collect(Collectors.toMap(ReadStrategy::getStrategyType, Function.identity()));

        this.writeStrategies = writeStrategyList.stream()
                .collect(Collectors.toMap(WriteStrategy::getStrategyType, Function.identity()));
    }

    public ReadStrategy getReadStrategy(ReadStrategyType type) {
        ReadStrategy strategy = readStrategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("未找到读策略: " + type);
        }
        return strategy;
    }

    public WriteStrategy getWriteStrategy(WriteStrategyType type) {
        WriteStrategy strategy = writeStrategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("未找到写策略: " + type);
        }
        return strategy;
    }

    /**
     * 根据业务场景自动选择策略
     */
    public ReadStrategy getDefaultReadStrategy(String cacheName) {
        if (cacheName.startsWith("config:")) {
            return getReadStrategy(ReadStrategyType.SCHEDULED_REFRESH);
//        } else if (cacheName.startsWith("hot:")) {
//            return getReadStrategy(ReadStrategyType.CACHE_FIRST);
        } else {
            return getReadStrategy(ReadStrategyType.LAZY_LOAD);
        }
    }

    public WriteStrategy getDefaultWriteStrategy(String cacheName) {
        if (cacheName.startsWith("user:") || cacheName.startsWith("order:")) {
            return getWriteStrategy(WriteStrategyType.CACHE_ASIDE);
        } else if (cacheName.startsWith("log:") || cacheName.startsWith("stat:")) {
            return getWriteStrategy(WriteStrategyType.WRITE_BEHIND);
        } else {
            return getWriteStrategy(WriteStrategyType.WRITE_THROUGH);
        }
    }
}