package com.wait.sync.write;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.WriteStrategyType;
import com.wait.util.BoundUtil;
import com.wait.util.instance.HashMappingUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 全量更新策略 - 合并多次更新为一次全量更新
 */
@Component
@Slf4j
public class SnapshotWriteStrategy implements WriteStrategy {

    @Autowired
    private BoundUtil boundUtil;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    private HashMappingUtil hashMappingUtil;

    /** 定时刷库延迟时间：60秒 */
    private static final long FLUSH_DELAY_MS = TimeUnit.SECONDS.toMillis(60);
    
    /** 重试延迟时间：60秒 */
    private static final long RETRY_DELAY_MS = TimeUnit.SECONDS.toMillis(60);

    // 存储每个key对应的最新实体状态和joinPoint
    private final Map<String, SnapshotTask> snapshotBuffer = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> flushTasks = new ConcurrentHashMap<>();

    @Override
    public void write(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        String key = param.getKey();

        try {
            // 1. 解析更新的实体对象
            Object updatedEntity = parseEntityFromArgs(joinPoint);

            // 2. 立即更新Redis Hash
            updateRedisHashImmediately(key, updatedEntity, param);

            // 3. 缓冲实体快照
            bufferSnapshotTask(key, updatedEntity, joinPoint);

            // 4. 启动定时刷库任务（统一由定时任务执行数据库写入）
            scheduleFlushTask(key);

            log.debug("SnapshotWrite: Buffered snapshot, key: {}", key);

        } catch (Exception e) {
            log.error("SnapshotWrite: Failed to process, key: {}", key, e);
            throw new RuntimeException("全量更新处理失败", e);
        }
    }

    @Override
    public void delete(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        try {
            boundUtil.del(param.getKey());
            snapshotBuffer.remove(param.getKey());
            cancelFlushTask(param.getKey());
            joinPoint.proceed();
            log.debug("SnapshotWrite: Delete completed, key: {}", param.getKey());
        } catch (Throwable e) {
            log.error("SnapshotWrite: Delete failed, key: {}", param.getKey(), e);
            throw new RuntimeException("删除操作失败", e);
        }
    }

    /**
     * 缓冲快照任务
     */
    private void bufferSnapshotTask(String key, Object newEntity, ProceedingJoinPoint joinPoint) {
        snapshotBuffer.compute(key, (k, existingTask) -> {
            if (existingTask == null) {
                log.debug("SnapshotWrite New snapshot task, key: {}, entity: {}, time: {}", key, newEntity,
                        System.currentTimeMillis());
                return new SnapshotTask(joinPoint, newEntity, System.currentTimeMillis());
            } else {
                // 合并更新：用新实体替换旧实体，保留joinPoint
                existingTask.setLatestEntity(mergeEntities(existingTask.getLatestEntity(), newEntity));
                existingTask.setLastUpdateTime(System.currentTimeMillis());
                log.debug("SnapshotWrite refresh snapshot task, key: {}, entity: {}, time: {}", key, newEntity,
                        System.currentTimeMillis());
                return existingTask;
            }
        });
    }

    /**
     * 合并实体属性（浅合并）
     */
    private Object mergeEntities(Object existing, Object newEntity) {
        // 简单实现：直接返回新实体（最后的状态覆盖之前的状态）
        // 实际可以根据业务需求实现更复杂的合并逻辑
        return newEntity;
    }

    /**
     * 刷写到数据库
     */
    private void flushToDatabase(String key) {
        SnapshotTask task = snapshotBuffer.remove(key);
        if (task == null) {
            return;
        }

        flushTasks.remove(key);

        try {
            // 修改joinPoint参数：使用最新的实体状态
            Object[] modifiedArgs = modifyJoinPointArgs(task.getJoinPoint(), task.getLatestEntity());

            // 执行更新
            task.getJoinPoint().proceed(modifiedArgs);

            log.info("SnapshotWrite: Flushed to database, key: {}", key);

            // 刷新成功后，如果还有新的数据等待刷新，继续创建定时任务
            SnapshotTask remainingTask = snapshotBuffer.get(key);
            if (remainingTask != null) {
                log.debug("More data pending after flush, reschedule task, key: {}", key);
                scheduleFlushTask(key);
            }

        } catch (Throwable e) {
            log.error("SnapshotWrite: Flush failed, key: {}", key, e);
            // 重试：将任务放回缓冲区，并重新创建任务
            snapshotBuffer.put(key, task);
            scheduleRetryTask(key);
        }
    }

    /**
     * 修改JoinPoint参数
     */
    private Object[] modifyJoinPointArgs(ProceedingJoinPoint joinPoint, Object latestEntity) {
        Object[] originalArgs = joinPoint.getArgs();
        Object[] modifiedArgs = new Object[originalArgs.length];

        System.arraycopy(originalArgs, 0, modifiedArgs, 0, originalArgs.length);

        // 假设第一个参数是要更新的实体对象
        if (modifiedArgs.length > 0) {
            modifiedArgs[0] = latestEntity;
        }

        return modifiedArgs;
    }

    // 其他辅助方法...
    private Object parseEntityFromArgs(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        return args != null && args.length > 0 ? args[0] : null;
    }

    private void updateRedisHashImmediately(String key, Object entity, CacheSyncParam param) {
        // 将实体转换为Map存储到Redis Hash
        Map<String, Object> fieldMap = hashMappingUtil.objectToMap(entity);
        if (!fieldMap.isEmpty()) {
            boundUtil.hSetAll(key, fieldMap);
            if (param.getExpireTime() != null) {
                boundUtil.expire(key, param.getExpireTime(), param.getTimeUnit());
            }
            log.debug("SnapshotWrite Updated Redis Hash, key: {}, value: {}", key, fieldMap);
        }
    }

    /**
     * 定时刷库任务
     * 只在任务不存在时创建，避免每次更新都重置定时器
     */
    private void scheduleFlushTask(String key) {
        // 如果任务已存在且未完成，不重置定时器，保持固定刷新周期
        ScheduledFuture<?> existingTask = flushTasks.get(key);
        if (existingTask != null && !existingTask.isDone() && !existingTask.isCancelled()) {
            log.debug("Flush task already scheduled, skip rescheduling, key: {}", key);
            return;
        }

        // 任务不存在或已取消/完成，创建新任务
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> flushToDatabase(key),
                new Date(System.currentTimeMillis() + FLUSH_DELAY_MS)
        );

        flushTasks.put(key, future);
        log.debug("Scheduled task created, key: {}, delay: {}ms", key, FLUSH_DELAY_MS);
    }

    private void scheduleRetryTask(String key) {
        // 重试任务取消旧任务后创建
        cancelFlushTask(key);
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> flushToDatabase(key),
                new Date(System.currentTimeMillis() + RETRY_DELAY_MS)
        );
        flushTasks.put(key, future);
        log.debug("Scheduled retry task, key: {}, delay: {}ms", key, RETRY_DELAY_MS);
    }

    private void cancelFlushTask(String key) {
        ScheduledFuture<?> task = flushTasks.get(key);
        if (task != null) {
            task.cancel(false);
            log.debug("cancel old task, key: {}", key);
            flushTasks.remove(key);
        }
    }

    @Override
    public WriteStrategyType getStrategyType() {
        return WriteStrategyType.SNAPSHOT_WRITE_BEHIND;
    }

    /**
     * 快照任务包装类
     */
    @Data
    @AllArgsConstructor
    private static class SnapshotTask {
        private ProceedingJoinPoint joinPoint;
        private Object latestEntity;
        private long lastUpdateTime;
    }
}