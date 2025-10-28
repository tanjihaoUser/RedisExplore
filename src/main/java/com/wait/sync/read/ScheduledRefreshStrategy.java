package com.wait.sync.read;

import com.wait.entity.CacheResult;
import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.ReadStrategyType;
import com.wait.util.AsyncSQLWrapper;
import com.wait.util.BoundUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时刷新缓存，主动推送，数据始终在缓存中，读请求的命中率极高，性能很好。
 * */
@Component
@Slf4j
public class ScheduledRefreshStrategy implements ReadStrategy {

    @Autowired
    private BoundUtil boundUtil;

    @Autowired
    @Qualifier("refreshScheduler")
    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    private AsyncSQLWrapper asyncSQLWrapper;

    private final Map<String, ScheduledFuture<?>> refreshTasks = new ConcurrentHashMap<>();

    @Override
    public <T> T read(CacheSyncParam<T> param, ProceedingJoinPoint joinPoint) {
        // 先尝试读缓存
        CacheResult<T> value = boundUtil.getWithRetry(param, 3);
        if (value.isHit()) {
            log.debug("Scheduled Refresh hit cache, key: {}, value: {}", param.getKey(), value.getValue());
            return value.getValue();
        }

        // 缓存不存在，同步加载并启动刷新
        return initializeWithScheduledRefresh(param, joinPoint);
    }

    private <T> T initializeWithScheduledRefresh(CacheSyncParam<T> param, ProceedingJoinPoint joinPoint) {
        try {
            // 同步加载数据
            asyncSQLWrapper.executeAspectMethod(param, joinPoint);
            boundUtil.writeWithRetry(param, 3);

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
                asyncSQLWrapper.executeAspectMethod(param, joinPoint);
                boundUtil.writeWithRetry(param, 3);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                String currentTime = sdf.format(new Date());
                log.debug("refresh success, key: {}, value: {}, time: {}", param.getKey(), param.getResult(), currentTime);
            } catch (Exception e) {
                log.error("schedule refresh fail: {}", param);
            }
        }, param.getRefreshInterval());

        refreshTasks.put(key, future);
        log.info("begin schedule refresh, key: {}, interval: {}ms", key, param.getRefreshInterval());
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
