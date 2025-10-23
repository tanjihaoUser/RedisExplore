package com.wait.sync.read;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.ReadStrategyType;
import com.wait.util.AsyncSQLWrapper;
import com.wait.util.BoundUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
@Slf4j
public class ScheduledRefreshStrategy implements ReadStrategy {

    @Autowired
    private BoundUtil boundUtil;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    private AsyncSQLWrapper asyncSQLWrapper;

    private final Map<String, ScheduledFuture<?>> refreshTasks = new ConcurrentHashMap<>();

    @Override
    public <T> T read(CacheSyncParam<T> param, ProceedingJoinPoint joinPoint) {
        // 先尝试读缓存
        T value = (T) boundUtil.getFromCache(param);
        if (value != null) {
            return value;
        }

        // 缓存不存在，同步加载并启动刷新
        return initializeWithScheduledRefresh(param, joinPoint);
    }

    private <T> T initializeWithScheduledRefresh(CacheSyncParam<T> param, ProceedingJoinPoint joinPoint) {
        try {
            // 同步加载数据
            asyncSQLWrapper.executeAspectMethod(param, joinPoint);
            boundUtil.cacheResult(param);

            // 启动定时刷新任务
            scheduleRefreshTask(param, joinPoint);

            return param.getResult();

        } catch (Exception e) {
            log.error("定时刷新策略初始化失败: {}", param, e);
            throw new RuntimeException("数据加载失败", e);
        }
    }

    private <T> void scheduleRefreshTask(CacheSyncParam<T> param, ProceedingJoinPoint joinPoint) {
        if (param.getRefreshInterval() == null) {
            return;
        }

        String key = param.getKey();

        // 取消已有的刷新任务
        cancelRefreshTask(key);

        // 创建新的刷新任务
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(() -> {
            try {
                asyncSQLWrapper.executeSimple(key, false, () -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable e) {
                        log.info("定时刷新失败: {}", key, e);
                        throw new RuntimeException(e);
                    }
                });
                log.debug("定时刷新完成: {}", param);
            } catch (Exception e) {
                log.error("定时刷新失败: {}", param);
            }
        }, param.getRefreshInterval());

        refreshTasks.put(key, future);
        log.info("启动定时刷新任务: {}, 间隔: {}ms", key, param.getRefreshInterval());
    }

    private void cancelRefreshTask(String key) {
        ScheduledFuture<?> existingTask = refreshTasks.get(key);
        if (existingTask != null) {
            existingTask.cancel(false);
            refreshTasks.remove(key);
        }
    }

    @Override
    public ReadStrategyType getStrategyType() {
        return ReadStrategyType.SCHEDULED_REFRESH;
    }
}
