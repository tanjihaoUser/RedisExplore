package com.wait.sync.write;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.WriteStrategyType;
import com.wait.util.AsyncSQLWrapper;
import com.wait.util.BoundUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CacheAsideStrategy implements WriteStrategy {

    @Autowired
    private BoundUtil boundUtil;

    @Autowired
    private AsyncSQLWrapper asyncSQLWrapper;

    @Override
    public void write(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        try {
            // 1. 先执行数据库操作
            asyncSQLWrapper.executeAspectMethod(param, joinPoint);

            // 2. 删除缓存（Cache-Aside模式）
            boundUtil.del(param.getKey());

            log.debug("Cache-Aside写策略执行完成: {}", param.getKey());

        } catch (Exception e) {
            log.error("Cache-Aside写策略失败: {}", param, e);
            throw new RuntimeException("写操作失败", e);
        }
    }

    @Override
    public void delete(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        try {
            // 1. 先执行数据库删除
            asyncSQLWrapper.executeAspectMethod(param, joinPoint);

            // 2. 删除缓存
            boundUtil.del(param.getKey());

            log.debug("Cache-Aside删除策略执行完成: {}", param.getKey());
        } catch (Exception e) {
            log.error("Cache-Aside删除策略失败: {}", param, e);
            throw new RuntimeException("删除操作失败", e);
        }
    }

    @Override
    public WriteStrategyType getStrategyType() {
        return WriteStrategyType.CACHE_ASIDE;
    }
}
