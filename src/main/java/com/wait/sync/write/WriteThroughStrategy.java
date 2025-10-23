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
public class WriteThroughStrategy implements WriteStrategy {

    @Autowired
    private BoundUtil boundUtil;

    @Autowired
    private AsyncSQLWrapper asyncSQLWrapper;

    @Override
    public void write(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        try {
            // 1. 先更新缓存
            boundUtil.cacheResult(param);

            // 2. 再更新数据库
            asyncSQLWrapper.executeAspectMethod(param, joinPoint);

            log.debug("Write-Through写策略执行完成: {}", param.getKey());

        } catch (Exception e) {
            // 写缓存成功但写数据库失败，需要回滚缓存
            boundUtil.del(param.getKey());
            log.error("Write-Through写策略失败，已回滚缓存: {}", param.getKey(), e);
            throw new RuntimeException("写操作失败", e);
        }
    }

    @Override
    public void delete(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        boundUtil.del(param.getKey());

        asyncSQLWrapper.executeAspectMethod(param, joinPoint);

        log.debug("Write-Through删除策略执行完成: {}", param.getKey());

    }

    @Override
    public WriteStrategyType getStrategyType() {
        return WriteStrategyType.WRITE_THROUGH;
    }

}
