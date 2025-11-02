package com.wait.sync.write;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.WriteStrategyType;
import com.wait.util.BoundUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
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

    /** 定时刷库延迟时间：2分钟 */
    private static final long FLUSH_DELAY_MS = TimeUnit.SECONDS.toMillis(120);
    
    /** 重试延迟时间：60秒 */
    private static final long RETRY_DELAY_MS = TimeUnit.SECONDS.toMillis(60);

    @Autowired
    private BoundUtil boundUtil;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    // 存储每个key对应的增量/覆盖任务和原始joinPoint
    private final Map<String, IncrementalTask> taskBuffer = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> flushTasks = new ConcurrentHashMap<>();

    @Override
    public void write(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        // 增量写策略不在AOP中先执行数据库操作，统一由定时任务执行
        // 所以 firstCallExecuted 始终为 false
        write(param, joinPoint, false);
    }

    /**
     * 内部方法：支持指定是否第一次调用且已执行过原始方法
     * 注意：现在增量策略不在AOP中先执行，所以 firstCallExecuted 通常为 false
     * 保留此参数用于向后兼容或特殊场景
     */
    public void write(CacheSyncParam param, ProceedingJoinPoint joinPoint, boolean firstCallExecuted) {
        String key = param.getKey();

        try {
            // 1. 解析此次调用的“多属性变更”，区分：增量字段 与 覆盖字段
            ChangeBundle changeBundle = parseChangeBundle(joinPoint);

            // 2. 立即更新Redis（能识别的场景尽量更新，识别不了则延后以DB为准）
            updateRedisImmediately(key, changeBundle, param, joinPoint);

            // 3. 缓冲任务（对同一key进行合并：增量相加、覆盖取最近值）
            bufferIncrementalTask(key, changeBundle, joinPoint, firstCallExecuted);

            // 4. 启动定时刷库任务（统一由定时任务执行数据库写入）
            scheduleFlushTask(key);

            log.debug("IncrementalWrite Buffered changes, key: {}, incIdx: {}, setIdx: {}, firstCallExecuted: {}",
                    key, changeBundle.numericIncrements.keySet(), changeBundle.latestReplacements.keySet(), firstCallExecuted);

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
     * 缓冲增量/覆盖任务
     * @param firstCallExecuted 是否为第一次调用且已执行过原始方法
     */
    private void bufferIncrementalTask(String key, ChangeBundle changeBundle, ProceedingJoinPoint joinPoint, boolean firstCallExecuted) {
        taskBuffer.compute(key, (k, existingTask) -> {
            if (existingTask == null) {
                long now = System.currentTimeMillis();
                IncrementalTask task = new IncrementalTask(joinPoint, new HashMap<>(), new HashMap<>(), now, now, firstCallExecuted);
                mergeIntoTask(task, changeBundle);
                log.debug("create new incremental task, key: {}, time: {}, firstCallExecuted: {}", key, now, firstCallExecuted);
                return task;
            } else {
                mergeIntoTask(existingTask, changeBundle);
                existingTask.setLastUpdateTime(System.currentTimeMillis());
                log.debug("update incremental task, key: {}, time: {}", key, existingTask.getLastUpdateTime());
                return existingTask;
            }
        });
    }

    private void mergeIntoTask(IncrementalTask task, ChangeBundle changeBundle) {
        // 合并增量：按参数下标相加，保持原始类型精度
        for (Map.Entry<Integer, Number> e : changeBundle.numericIncrements.entrySet()) {
            Integer idx = e.getKey();
            Number newValue = e.getValue();
            task.getNumericDeltas().merge(idx, newValue, this::addNumbers);
        }
        // 合并覆盖：按参数下标覆盖为最新值（直接存储原始类型）
        task.getLatestValues().putAll(changeBundle.latestReplacements);
    }

    /**
     * 智能合并两个 Number，保持精度：
     * - 如果两个都是整数类型（Integer, Long等），返回 Long
     * - 如果两个都是 Float，返回 Float（保持单精度）
     * - 如果至少一个是 Double 或混合类型，返回 Double（保持最高精度）
     */
    private Number addNumbers(Number existing, Number newValue) {
        // 如果两者都是整数类型，使用 Long 累加
        if (isIntegerType(existing) && isIntegerType(newValue)) {
            return existing.longValue() + newValue.longValue();
        }
        // 如果两者都是 Float，返回 Float
        if (existing instanceof Float && newValue instanceof Float) {
            return existing.floatValue() + newValue.floatValue();
        }
        // 其他情况（Double、混合类型等）使用 Double 累加以保持最高精度
        return existing.doubleValue() + newValue.doubleValue();
    }

    /**
     * 判断是否为整数类型（Integer, Long, Short, Byte）
     */
    private boolean isIntegerType(Number num) {
        return num instanceof Integer || num instanceof Long ||
               num instanceof Short || num instanceof Byte;
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

    /**
     * 刷写到数据库 - 核心方法
     */
    private void flushToDatabase(String key) {
        IncrementalTask task = taskBuffer.remove(key);
        if (task == null || (task.getNumericDeltas().isEmpty() && task.getLatestValues().isEmpty())) {
            return;
        }

        flushTasks.remove(key);

        // 注意：现在增量策略不在AOP中先执行，所以 firstCallExecuted 通常为 false
        // 保留此判断逻辑用于向后兼容，但实际不会触发跳过逻辑
        if (task.isFirstCallExecuted()) {
            // 如果任务创建后没有后续更新（lastUpdateTime == 创建时间），说明只有第一次调用
            // 注意：mergeIntoTask 会更新 lastUpdateTime，所以如果有后续调用，lastUpdateTime 会大于创建时间
            if (task.getLastUpdateTime() == task.getCreateTime() || 
                (task.getNumericDeltas().isEmpty() && task.getLatestValues().isEmpty())) {
                log.debug("IncrementalWrite Skip flush for first call only (already executed), key: {}", key);
                return;
            }
        }

        try {
            // 基于原始参数进行合并：
            //  数值型增量：用累计增量替换对应参数（由SQL执行 "col = col + #{arg}"）
            //  覆盖型字段：用最新值替换对应参数
            Object[] modifiedArgs = modifyJoinPointArgs(task);

            // 使用修改后的参数执行原始MyBatis方法
            task.getJoinPoint().proceed(modifiedArgs);

            log.info("IncrementalWrite Flushed to database, key: {}, deltaArgs: {}, latestArgs: {}, firstCallExecuted: {}",
                    key, task.getNumericDeltas().keySet(), task.getLatestValues().keySet(), task.isFirstCallExecuted());

            // 刷新成功后，如果还有新的数据等待刷新，继续创建定时任务
            IncrementalTask remainingTask = taskBuffer.get(key);
            if (remainingTask != null && 
                (!remainingTask.getNumericDeltas().isEmpty() || !remainingTask.getLatestValues().isEmpty())) {
                log.debug("More data pending after flush, reschedule task, key: {}", key);
                scheduleFlushTask(key);
            }

        } catch (Throwable e) {
            log.error("IncrementalWrite Flush failed, key: {}", key, e);
            // 重试：将任务放回缓冲区，并重新创建任务
            taskBuffer.put(key, task);
            scheduleRetryTask(key);
        }
    }

    /**
     * 修改JoinPoint参数 - 关键实现
     * ProceedingJoinPoint.getArgs() 返回的数组可能是一个副本，也可能是原始数组的引用。
     * 不能直接使用，需要复制一份。
     */
    private Object[] modifyJoinPointArgs(IncrementalTask task) {
        ProceedingJoinPoint joinPoint = task.getJoinPoint();
        Object[] originalArgs = joinPoint.getArgs();
        Object[] modifiedArgs = new Object[originalArgs.length];

        // 复制原始参数
        System.arraycopy(originalArgs, 0, modifiedArgs, 0, originalArgs.length);

        // 读取目标方法参数类型
        MethodSignature ms = (MethodSignature) joinPoint.getSignature();
        Class<?>[] paramTypes = ms.getMethod().getParameterTypes();

        // 先应用覆盖型字段（最新值）
        for (Map.Entry<Integer, Object> e : task.getLatestValues().entrySet()) {
            int idx = e.getKey();
            if (idx >= 0 && idx < modifiedArgs.length) {
                Object val = e.getValue();
                Class<?> target = idx < paramTypes.length ? paramTypes[idx] : (val != null ? val.getClass() : Object.class);
                modifiedArgs[idx] = convertToType(val, target);
            }
        }

        // 再应用数值型增量（替换为累计增量值，由SQL完成 col = col + #{arg}）
        for (Map.Entry<Integer, Number> e : task.getNumericDeltas().entrySet()) {
            int idx = e.getKey();
            if (idx >= 0 && idx < modifiedArgs.length) {
                Number val = e.getValue();
                Class<?> target = idx < paramTypes.length ? paramTypes[idx] : Number.class;
                modifiedArgs[idx] = convertToType(val, target);
            }
        }

        return modifiedArgs;
    }

    private Object convertToType(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        // 处理原始类型和包装类型的转换
        if (value instanceof Number) {
            Number n = (Number) value;
            // 直接处理原始类型，避免二次判断
            if (targetType == int.class || targetType == Integer.class) {
                return n.intValue();
            }
            if (targetType == long.class || targetType == Long.class) {
                return n.longValue();
            }
            if (targetType == double.class || targetType == Double.class) {
                return n.doubleValue();
            }
            if (targetType == float.class || targetType == Float.class) {
                return n.floatValue();
            }
            if (targetType == short.class || targetType == Short.class) {
                return n.shortValue();
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return n.byteValue();
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                return n.intValue() != 0;
            }
        }

        try {
            if (targetType == String.class) {
                return String.valueOf(value);
            }
        } catch (Exception ignore) {
        }
        // 回退：直接返回，交由反射校验或抛错
        return value;
    }

    // 解析一次调用的变更集合：
    //  - 参数索引0通常为主键，保留不动
    //  - 对于 Number 类型参数：视为增量；若被判定为"时间戳"，则作为覆盖
    //  - 对于非 Number：作为覆盖型字段
    private ChangeBundle parseChangeBundle(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        Map<Integer, Number> numericIncrements = new HashMap<>();
        Map<Integer, Object> latestReplacements = new HashMap<>();

        if (args == null || args.length == 0) {
            return new ChangeBundle(numericIncrements, latestReplacements);
        }

        for (int i = 1; i < args.length; i++) { // 跳过一般为主键的第0个参数
            Object arg = args[i];
            if (arg == null) {
                continue;
            }
            if (arg instanceof Number) {
                Number num = (Number) arg;
                // 若像时间戳（毫秒级，>10^11），视为覆盖，否则视为增量
                // 判断时使用 longValue，但存储时保持原始类型
                if (isLikelyTimestamp(num.longValue())) {
                    // 时间戳直接存储原始类型，不做转换
                    latestReplacements.put(i, arg);
                } else {
                    // 增量值直接存储原始 Number 类型，保持精度
                    numericIncrements.put(i, num);
                }
            } else {
                // 非 Number 类型直接存储原始值
                latestReplacements.put(i, arg);
            }
        }

        return new ChangeBundle(numericIncrements, latestReplacements);
    }

    private boolean isLikelyTimestamp(long val) {
        // 2001-09-09 01:46:40 (10^12) 之前的毫秒时间戳较少出现在业务中，这里采用保守阈值
        return val > 1_000_000_000_00L; // 10^11
    }

    private void updateRedisImmediately(String key, ChangeBundle changeBundle, CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        try {
            // 仅在可明确映射的情况下执行即时更新，避免误写
            switch (param.getCacheType()) {
                case STRING:
                    // STRING 场景默认视为简单计数器（第一个非主键Number参数为增量）
                    Map<Integer, Number> inc = changeBundle.numericIncrements;
                    if (!inc.isEmpty()) {
                        // 取所有元素的和作为delta累加（使用 Long 类型，Redis 只支持整数）
                        long delta = inc.values().stream()
                                .mapToLong(Number::longValue)
                                .sum();
                        boundUtil.incrBy(key, delta);
                    }
                    break;
                case HASH: {
                    Map<Integer, String> idxToName = getParamIndexToName(joinPoint);
                    // 批量覆盖：聚合为一次 HMSET
                    Map<String, Object> setMap = new HashMap<>();
                    for (Map.Entry<Integer, Object> e : changeBundle.latestReplacements.entrySet()) {
                        String field = idxToName.get(e.getKey());
                        if (field != null) {
                            setMap.put(field, e.getValue());
                        }
                    }
                    if (!setMap.isEmpty()) {
                        boundUtil.hSetAll(key, setMap);
                    }
                    // 数值增量：逐个 HINCRBY（Redis HINCRBY 只支持整数，使用 longValue）
                    for (Map.Entry<Integer, Number> e : changeBundle.numericIncrements.entrySet()) {
                        String field = idxToName.get(e.getKey());
                        if (field != null) {
                            boundUtil.hIncrBy(key, field, e.getValue().longValue());
                        }
                    }
                    break;
                }
                default:
                    // 其他类型暂不做即时更新
                    break;
            }

            if (param.getExpireTime() != null) {
                boundUtil.expire(key, param.getExpireTime(), param.getTimeUnit());
            }
            log.debug("IncrementalWrite update redis done, key: {}, changeBundle: {}", key, changeBundle);
        } catch (Exception e) {
            log.warn("IncrementalWrite: immediate redis update skipped, key: {} err: {}", key, e.getMessage());
        }
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
        return WriteStrategyType.INCREMENTAL_WRITE_BEHIND;
    }

    /**
     * 增量任务包装类
     */
    @Data
    @AllArgsConstructor
    private static class IncrementalTask {
        private ProceedingJoinPoint joinPoint;
        // 参数下标 -> 累计增量值（用于 SQL: col = col + #{arg} 的参数）
        // 存储原始 Number 类型，避免精度丢失（Long, Integer, Double, Float等）
        private Map<Integer, Number> numericDeltas;
        // 参数下标 -> 最新值（用于覆盖型字段：时间、字符串等）
        // 直接存储原始类型，不做类型转换
        private Map<Integer, Object> latestValues;
        private long createTime; // 任务创建时间
        private long lastUpdateTime; // 最后一次更新时间
        private boolean firstCallExecuted; // 是否为第一次调用且已执行过原始方法
    }

    @Data
    @AllArgsConstructor
    private static class ChangeBundle {
        // 存储原始 Number 类型，避免精度丢失
        private Map<Integer, Number> numericIncrements;
        // 直接存储原始类型，不做类型转换
        private Map<Integer, Object> latestReplacements;

        @Override
        public String toString() {
            return "ChangeBundle{" + "numericIncrements=" + numericIncrements +
                    ", latestReplacements=" + latestReplacements + '}';
        }
    }

    /**
     * 解析位置到参数名的映射（通常第0个参数为主键，这里也解析出来，调用侧可忽略）
     * 基于 MyBatis @Param 注解解析“参数下标 -> 参数名”
     * 没有注解使用参数名，参数名解析失败使用默认命名（arg0, arg1, ...）
     */
    private Map<Integer, String> getParamIndexToName(ProceedingJoinPoint joinPoint) {
        Map<Integer, String> map = new HashMap<>();
        try {
            MethodSignature ms = (MethodSignature) joinPoint.getSignature();
            Method method = ms.getMethod();

            // 获取参数名（需要编译时开启参数名保留）
            String[] parameterNames = ms.getParameterNames();
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();

            for (int i = 0; i < parameterAnnotations.length; i++) {
                String paramName = null;

                // 1. 优先使用 @Param 注解的值
                for (Annotation annotation : parameterAnnotations[i]) {
                    if (annotation instanceof Param) {
                        paramName = ((Param) annotation).value();
                        break;
                    }
                }

                // 2. 如果没有 @Param 注解，使用参数名
                if (paramName == null && parameterNames != null && i < parameterNames.length) {
                    paramName = parameterNames[i];
                }

                // 3. 如果参数名也不可用，使用默认命名（参数索引）
                if (paramName == null) {
                    paramName = "arg" + i;
                }

                map.put(i, paramName);
            }

        } catch (Exception e) {
            log.warn("解析参数名失败: {}", e.getMessage());
        }
        return map;
    }
}