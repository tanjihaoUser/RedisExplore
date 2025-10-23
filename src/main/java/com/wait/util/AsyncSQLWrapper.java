package com.wait.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.wait.entity.CacheSyncParam;
import com.wait.service.MQServiceImpl;
import com.wait.util.message.CompensationMsg;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AsyncSQLWrapper {

    @Autowired
    private MQServiceImpl mqService;

    private final ExecutorService executor = new ThreadPoolExecutor(
            5, 20, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadFactoryBuilder().setNameFormat("cache-pool-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 统一的数据库操作封装类
     */
    @Data
    private static class DatabaseOperation<T> {
        private final Callable<T> callable;
        private final Runnable runnable;
        private T result;
        private Exception error;

        public DatabaseOperation(Callable<T> callable) {
            this.callable = callable;
            this.runnable = null;
        }

        public DatabaseOperation(Runnable runnable) {
            this.callable = null;
            this.runnable = runnable;
        }

        /**
         * 执行操作并返回结果
         */
        public T execute() throws Exception {
            try {
                if (callable != null) {
                    result = callable.call();
                    return result;
                } else if (runnable != null) {
                    runnable.run();
                    return null;
                } else {
                    throw new IllegalStateException("No operation specified");
                }
            } catch (Exception e) {
                error = e;
                throw e;
            }
        }

        public boolean hasReturnValue() {
            return callable != null;
        }
    }

    /**
     * 执行切面方法 - 统一入口
     */
    @SuppressWarnings("unchecked")
    public <T> T executeAspectMethod(CacheSyncParam<T> param, ProceedingJoinPoint joinPoint) {
        DatabaseOperation<T> operation = new DatabaseOperation<>(() -> {
            try {
                return (T) joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException("JoinPoint execution failed", e);
            }
        });
        return executeOperation(param, operation);
    }

    /**
     * 统一的操作执行方法
     */
    private <T> T executeOperation(CacheSyncParam<T> param, DatabaseOperation<T> operation) {
        if (Boolean.TRUE.equals(param.getIsExecuteASync())) {
            return executeAsync(param, operation);
        } else {
            return executeSync(param, operation);
        }
    }

    /**
     * 异步执行
     */
    private <T> T executeAsync(CacheSyncParam<T> param, DatabaseOperation<T> operation) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return executeWithRetry(operation, param.getKey());
            } catch (Exception e) {
                log.error("Async operation failed: {}, send compensation message", param.getKey(), e);
                sendToCompensationQueue(param, e, operation);
                throw new RuntimeException(e);
            }
        }, executor);

        // 异步操作立即返回
        return param.getNewValue() != null ? param.getNewValue() : null;
    }

    /**
     * 同步执行
     */
    private <T> T executeSync(CacheSyncParam<T> param, DatabaseOperation<T> operation) {
        try {
            T result = executeWithRetry(operation, param.getKey());
            param.setResult(result);
            return result;
        } catch (Exception e) {
            log.error("Sync operation failed: {}", param.getKey(), e);
            throw new RuntimeException("Operation failed after retries", e);
        }
    }

    /**
     * 带重试的执行逻辑
     */
    @Retryable(
            value = {SQLException.class, DataAccessException.class, RuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000)
    )
    public <T> T executeWithRetry(DatabaseOperation<T> operation, String key) throws Exception {
        RetryContext context = RetrySynchronizationManager.getContext();
        int retryCount = (context != null ? context.getRetryCount() : 0) + 1;

        log.info("Executing database operation, key: {}, attempt: {}", key, retryCount);
        return operation.execute();
    }

    /**
     * 发送到补偿队列
     */
    private <T> void sendToCompensationQueue(CacheSyncParam<T> param, Exception error,
                                             DatabaseOperation<T> operation) {
        try {
            CompensationMsg<T> message = CompensationMsg.<T>builder()
                    .originalParam(param)
                    .failReason(error.getMessage())
                    .failTime(System.currentTimeMillis())
                    .build();

            mqService.sendMessage(MQServiceImpl.COMPENSATION_TOPIC, param.getKey(), message);
        } catch (Exception mqError) {
            log.error("Failed to send to compensation queue: {}", param.getKey(), mqError);
        }
    }

    /**
     * 关闭线程池
     */
    @PreDestroy
    public void destroy() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 简化调用 - 统一接口
     */
    public <T> T executeSimple(String key, boolean async, Callable<T> operation) {
        CacheSyncParam<T> param = CacheSyncParam.<T>builder()
                .key(key)
                .isExecuteASync(async)
                .build();

        return executeWithReturn(param, operation);
    }

    /**
     * 简化调用 - 无返回值版本
     */
    public void executeSimple(String key, boolean async, Runnable operation) {
        CacheSyncParam<Void> param = CacheSyncParam.<Void>builder()
                .key(key)
                .isExecuteASync(async)
                .build();

        executeWithoutReturn(param, operation);
    }

    /**
     * 执行有返回值的操作
     */
    public <T> T executeWithReturn(CacheSyncParam<T> param, Callable<T> dbOperation) {
        DatabaseOperation<T> operation = new DatabaseOperation<>(dbOperation);
        return executeOperation(param, operation);
    }

    /**
     * 执行无返回值的操作
     */
    public void executeWithoutReturn(CacheSyncParam<Void> param, Runnable dbOperation) {
        DatabaseOperation<Void> operation = new DatabaseOperation<>(dbOperation);
        executeOperation(param, operation);
    }

    /**
     * 批量执行多个操作
     */
    public <T> List<T> executeBatch(List<BatchOperation<T>> operations) {
        List<CompletableFuture<T>> futures = new ArrayList<>();

        for (BatchOperation<T> batchOp : operations) {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
                try {
                    DatabaseOperation<T> operation = new DatabaseOperation<>(batchOp.getOperation());
                    return executeWithRetry(operation, batchOp.getKey());
                } catch (Exception e) {
                    log.error("Batch operation failed: {}", batchOp.getKey(), e);
                    throw new RuntimeException(e);
                }
            }, executor);
            futures.add(future);
        }

        // 等待所有操作完成
        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    /**
     * 批量操作封装类
     */
    @Data
    @AllArgsConstructor
    public static class BatchOperation<T> {
        private String key;
        private Callable<T> operation;
        private boolean async;
    }

}