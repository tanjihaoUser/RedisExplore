package com.wait.sync.write;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.WriteStrategyType;
import com.wait.util.BoundUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 增量更新策略 - 通过修改方法参数实现批量增量更新
 */
@Component
@Slf4j
public class IncrementalWriteStrategy implements WriteStrategy {

    @Autowired
    private BoundUtil boundUtil;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    // 存储每个key对应的增量值和原始joinPoint
    private final Map<String, IncrementalTask> taskBuffer = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> flushTasks = new ConcurrentHashMap<>();

    @Override
    public void write(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        String key = param.getKey();

        try {
            // 1. 解析增量值
            long delta = parseDeltaValue(joinPoint);

            // 2. 立即更新Redis
            updateRedisImmediately(key, delta, param);

            // 3. 缓冲增量任务
            bufferIncrementalTask(key, delta, joinPoint);

            // 4. 启动定时刷库任务
            scheduleFlushTask(key);

            log.debug("IncrementalWrite Buffered delta, key: {}, delta: {}", key, delta);

        } catch (Exception e) {
            log.error("IncrementalWrite: Failed to process, key: {}", key, e);
            throw new RuntimeException("增量更新处理失败", e);
        }
    }

    @Override
    public void delete(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        // 删除操作直接执行，不进行缓冲
        try {
            // 1. 删除Redis缓存
            boundUtil.del(param.getKey());

            // 2. 清理缓冲区中的任务
            taskBuffer.remove(param.getKey());
            cancelFlushTask(param.getKey());

            // 3. 执行原始删除方法
            joinPoint.proceed();

            log.debug("IncrementalWrite: Delete completed, key: {}", param.getKey());

        } catch (Throwable e) {
            log.error("IncrementalWrite: Delete failed, key: {}", param.getKey(), e);
            throw new RuntimeException("删除操作失败", e);
        }
    }

    /**
     * 缓冲增量任务
     */
    private void bufferIncrementalTask(String key, long newDelta, ProceedingJoinPoint joinPoint) {
        taskBuffer.compute(key, (k, existingTask) -> {
            if (existingTask == null) {
                log.debug("create new task, key: {}, delta: {}, time: {}", key, newDelta, System.currentTimeMillis());
                // 新任务：保存原始joinPoint和增量值
                return new IncrementalTask(joinPoint, newDelta, System.currentTimeMillis());
            } else {
                // 合并增量：只更新delta，保留原始joinPoint
                existingTask.setTotalDelta(existingTask.getTotalDelta() + newDelta);
                log.debug("update task, key: {}, delta: {}, time: {}", key, newDelta, existingTask.getLastUpdateTime());
                existingTask.setLastUpdateTime(System.currentTimeMillis());
                return existingTask;
            }
        });
    }

    /**
     * 定时刷库任务
     */
    private void scheduleFlushTask(String key) {
        cancelFlushTask(key);

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> flushToDatabase(key),
                new Date(System.currentTimeMillis() + getFlushDelay())
        );

        flushTasks.put(key, future);
        log.debug("Scheduled flush task for key: {}, delay: {}ms", key, getFlushDelay());
    }

    /**
     * 刷写到数据库 - 核心方法
     */
    private void flushToDatabase(String key) {
        IncrementalTask task = taskBuffer.remove(key);
        if (task == null || task.getTotalDelta() == 0) {
            return;
        }

        flushTasks.remove(key);

        try {
            // 修改joinPoint的参数：将单个增量改为累计增量
            Object[] modifiedArgs = modifyJoinPointArgs(task.getJoinPoint(), task.getTotalDelta());

            // 使用修改后的参数执行原始MyBatis方法
            task.getJoinPoint().proceed(modifiedArgs);

            log.info("IncrementalWrite Flushed to database, key: {}, totalDelta: {}",
                    key, task.getTotalDelta());

        } catch (Throwable e) {
            log.error("IncrementalWrite Flush failed, key: {}, delta: {}",
                    key, task.getTotalDelta(), e);
            // 重试：将任务放回缓冲区
            taskBuffer.put(key, task);
            scheduleRetryTask(key);
        }
    }

    /**
     * 修改JoinPoint参数 - 关键实现
     * ProceedingJoinPoint.getArgs() 返回的数组可能是一个副本，也可能是原始数组的引用。
     * 不能直接使用，需要复制一份。
     */
    private Object[] modifyJoinPointArgs(ProceedingJoinPoint joinPoint, long totalDelta) {
        Object[] originalArgs = joinPoint.getArgs();
        Object[] modifiedArgs = new Object[originalArgs.length];

        // 复制原始参数
        System.arraycopy(originalArgs, 0, modifiedArgs, 0, originalArgs.length);

        // 修改增量参数（假设第一个参数是主键，第二个是增量值）
        if (modifiedArgs.length > 1 && modifiedArgs[1] instanceof Number) {
            modifiedArgs[1] = totalDelta; // 替换为累计增量
        }

        return modifiedArgs;
    }

    // 解析增量delta，默认第一个参数为增量值，没有参数时默认为1
    private long parseDeltaValue(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0 && args[0] instanceof Number) {
            return ((Number) args[0]).longValue();
        }
        return 1L; // 默认增量
    }

    private void updateRedisImmediately(String key, long delta, CacheSyncParam param) {
        Long newValue = boundUtil.incrBy(key, delta);
        if (param.getExpireTime() != null) {
            boundUtil.expire(key, param.getExpireTime(), param.getTimeUnit());
        }
    }

    private void scheduleRetryTask(String key) {
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> flushToDatabase(key),
                new Date(System.currentTimeMillis() + getRetryDelay())
        );
        flushTasks.put(key, future);
    }

    private void cancelFlushTask(String key) {
        ScheduledFuture<?> task = flushTasks.get(key);
        if (task != null) {
            task.cancel(false);
            log.debug("cancel old task");
            flushTasks.remove(key);
        }
    }

    private long getFlushDelay() {
        return TimeUnit.SECONDS.toMillis(5);
    }

    private long getRetryDelay() {
        return TimeUnit.SECONDS.toMillis(10);
    }

    @Override
    public WriteStrategyType getStrategyType() {
        return WriteStrategyType.INCREMENTAL_WRITE_BEHIND;
    }

    /**
     * 增量任务包装类
     */
    @Data
    @AllArgsConstructor
    private static class IncrementalTask {
        private ProceedingJoinPoint joinPoint;
        private long totalDelta;
        private long lastUpdateTime;
    }
}