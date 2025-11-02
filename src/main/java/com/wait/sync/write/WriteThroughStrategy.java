package com.wait.sync.write;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.WriteStrategyType;
import com.wait.util.AsyncSQLWrapper;
import com.wait.util.BoundUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 适用于对数据一致性要求极高、零容忍不一致的场景。先写缓存，后更新数据库
 * 极少在互联网高并发场景使用，但在金融计费、银行账户余额等场景下，需要保证数据强一致性，必须使用该策略
 * */
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
            // 默认使用第一个方法参数作为缓存值
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                param.setNewValue(args[0]);
            }

            // 1. 先更新缓存
            boundUtil.writeWithRetry(param, 3);

            // 2. 再更新数据库
            asyncSQLWrapper.executeAspectMethod(param, joinPoint);

            log.debug("Write-Through write strategy execute completed, key: {}", param.getKey());

        } catch (Exception e) {
            // 写缓存成功但写数据库失败，需要回滚缓存
            boundUtil.del(param.getKey());
            log.error("Write-Through write strategy execute failed, delete cache, key: {}", param.getKey(), e);
            throw new RuntimeException("write-through write strategy failed", e);
        }
    }

    @Override
    public void delete(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        boundUtil.del(param.getKey());

        asyncSQLWrapper.executeAspectMethod(param, joinPoint);

        log.debug("Write-Through delete strategy execute completed, key: {}", param.getKey());

    }

    @Override
    public WriteStrategyType getStrategyType() {
        return WriteStrategyType.WRITE_THROUGH;
    }

}
