package com.wait.sync.write;

import com.wait.entity.CacheSyncParam;
import com.wait.util.AsyncSQLWrapper;
import com.wait.util.message.AsyncDataMsg;
import com.wait.entity.type.DataOperationType;
import com.wait.entity.type.WriteStrategyType;
import com.wait.service.MQServiceImpl;
import com.wait.util.BoundUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * todo 参见gemini建议总结和修改方法
 * */
@Component
@Slf4j
public class MemoryQueueWriteBehindStrategy implements WriteStrategy, DisposableBean {

    @Autowired
    private BoundUtil boundUtil;

    @Autowired
    private MQServiceImpl mqService;

    @Autowired
    private AsyncSQLWrapper asyncSQLWrapper;

    // 内存队列：存储待写入数据库的任务
    private final BlockingQueue<AsyncDataMsg> writeQueue = new LinkedBlockingQueue<>(10000);
    private final BlockingQueue<ProceedingJoinPoint> operationQueue = new LinkedBlockingQueue<>(10000);

    // 批量写入执行器
    private final ScheduledExecutorService batchExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "WriteBehind-BatchWriter")
    );

    // 控制批量写入任务的运行状态
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledFuture<?> batchTaskFuture;

    // 批量写入配置
    private static final int BATCH_SIZE = 100; // 每批处理数量
    private static final long BATCH_INTERVAL_MS = 1000; // 批处理间隔(ms)
    private static final int MAX_RETRY_COUNT = 3; // 最大重试次数

    @PostConstruct
    public void init() {
        startBatchWriter();
        log.info("内存队列Write-Behind策略初始化完成");
    }

    @Override
    public void write(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        try {
            // 1. 立即写入缓存（快速响应）
            if (param.getNewValue() != null) {
                boundUtil.cacheResult(param);
                log.debug("Write-Behind: 缓存写入成功, key: {}, value: {}", param.getKey(), param.getNewValue());
            }

            // 2. 创建写回任务并加入队列（将数据库操作包装到任务中）
            AsyncDataMsg task = new AsyncDataMsg(DataOperationType.UPDATE, param);

            boolean offered = writeQueue.offer(task, 100, TimeUnit.MILLISECONDS);
            if (!offered) {
                log.warn("Write-Behind: 队列已满，任务被丢弃, task: {}", task);
                // 队列满时降级为同步写入
                asyncSQLWrapper.executeAspectMethod(param, joinPoint);
            }

            log.debug("Write-Behind: 任务已加入队列, param: {}", param);

        } catch (Exception e) {
            log.error("Write-Behind写入失败, param: {}", param, e);
            // 降级为同步写入
            asyncSQLWrapper.executeAspectMethod(param, joinPoint);
        }
    }

    @Override
    public void delete(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        try {
            String key = param.getKey();
            // 1. 立即删除缓存
            boundUtil.del(key);
            log.debug("Write-Behind: 缓存删除成功, key: {}", key);

            // 2. 创建删除任务并加入队列（将删除操作包装到任务中）
            AsyncDataMsg<Object> task = new AsyncDataMsg<>(DataOperationType.DELETE, param);

            boolean offered = writeQueue.offer(task, 100, TimeUnit.MILLISECONDS);
            if (!offered) {
                log.warn("Write-Behind: 队列已满，删除任务被丢弃, key: {}", key);
                // 队列满时降级为同步删除
                asyncSQLWrapper.executeAspectMethod(param, joinPoint);
                return;
            }

            log.debug("Write-Behind: 删除任务已加入队列, key: {}", key);

        } catch (Exception e) {
            log.error("Write-Behind删除失败, param: {}", param, e);
            // 降级为同步删除
            asyncSQLWrapper.executeAspectMethod(param, joinPoint);
        }
    }

    /**
     * 启动批量写入任务
     */
    private void startBatchWriter() {
        if (running.compareAndSet(false, true)) {
            batchTaskFuture = batchExecutor.scheduleAtFixedRate(
                    this::processBatch,
                    0, BATCH_INTERVAL_MS, TimeUnit.MILLISECONDS
            );
            log.info("Write-Behind批量写入任务已启动");
        }
    }

    /**
     * 批量处理写回任务
     */
    private void processBatch() {
        try {
            List<AsyncDataMsg> batch = new ArrayList<>(BATCH_SIZE);
            List<ProceedingJoinPoint> operations = new ArrayList<>(BATCH_SIZE);

            // 从队列中取出一批任务
            writeQueue.drainTo(batch, BATCH_SIZE);
            operationQueue.drainTo(operations, BATCH_SIZE);

            if (batch.isEmpty()) {
                return;
            }

            log.debug("开始处理批量写回任务，数量: {}", batch.size());

            // 处理批量任务
            processBatchOperations(batch, operations);

        } catch (Exception e) {
            log.error("批量写回任务处理失败", e);
        }
    }

    /**
     * 处理批量操作
     */
    private void processBatchOperations(List<AsyncDataMsg> batch, List<ProceedingJoinPoint> operations) {
        // 过滤有效任务

        if (batch.isEmpty()) {
            log.warn("批量中没有有效任务");
            return;
        }

        int successCount = 0;
        int failureCount = 0;

        // 逐个执行任务（可以根据需要改为真正的批量操作）
        for (int i = 0; i < batch.size(); i++) {
            AsyncDataMsg<Object> task = batch.get(i);
            ProceedingJoinPoint operation = operations.get(i);
            try {
                operation.proceed();
                log.info("task execute success: {}", task);
                successCount++;
            } catch (Throwable e) {
                log.info("task execute failed: {}", task, e);
                failureCount++;
                handleTaskFailure(task, operation);
            }
        }

        log.info("批量任务处理完成: 成功={}, 失败={}, 总数={}",
                successCount, failureCount, batch.size());
    }

    /**
     * 处理任务失败（重试机制）
     */
    private void handleTaskFailure(AsyncDataMsg<Object> task, ProceedingJoinPoint operation) {
        if (task.getRetryCount() < MAX_RETRY_COUNT) {
            task.setRetryCount(task.getRetryCount() + 1);
            log.warn("任务执行失败，准备重试: {} - 重试次数: {}", task.getKey(), task.getRetryCount());

            // 重新加入队列（可以加入延迟重试队列）
            boolean offered = writeQueue.offer(task) && operationQueue.offer(operation);
            if (!offered) {
                log.error("重试任务加入队列失败，任务丢失: {}", task.getKey());
            }
        } else {
            log.error("任务重试次数耗尽，最终失败: {} - {}", task, operation);
            // 可以记录到死信队列或发送告警
            handleFinalFailure(task, operation);
        }
    }

    /**
     * 处理最终失败的任务
     */
    private void handleFinalFailure(AsyncDataMsg<Object> task, ProceedingJoinPoint joinPoint) {
        // 记录到错误日志
        log.error("Write-Behind任务最终失败: key={}, operation={}", task.getKey(), task);

        // 可以发送告警、记录到文件或数据库
        // alertService.sendAlert("Write-Behind任务失败", task.toString());

        // 对于重要数据，可以尝试最后一次同步执行
        if (isCriticalData(task)) {
            log.warn("重要数据任务失败，尝试同步执行: {}", task.getKey());
            try {
                joinPoint.proceed();
                log.info("重要数据任务同步执行成功: {}", task.getKey());
            } catch (Throwable ex) {
                log.error("重要数据任务同步执行也失败: {}", task.getKey(), ex);
            }
        }
    }

    /**
     * 判断是否为重要数据（需要特殊处理）
     */
    private boolean isCriticalData(AsyncDataMsg<Object> task) {
        // 根据业务规则判断，例如订单、支付相关数据
        return task.getKey().startsWith("order:") ||
                task.getKey().startsWith("payment:") ||
                task.getKey().startsWith("account:");
    }

    /**
     * 优雅关闭
     */
    @Override
    public void destroy() throws Exception {
        running.set(false);

        if (batchTaskFuture != null) {
            batchTaskFuture.cancel(true);
        }

        batchExecutor.shutdown();
        if (!batchExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
            batchExecutor.shutdownNow();
        }

        // 关闭前处理剩余任务
        if (!writeQueue.isEmpty()) {
            log.info("关闭前处理剩余{}个写回任务", writeQueue.size());
            processBatch();
        }

        log.info("内存队列Write-Behind策略已关闭");
    }

    @Override
    public WriteStrategyType getStrategyType() {
        return WriteStrategyType.WRITE_BEHIND;
    }
}