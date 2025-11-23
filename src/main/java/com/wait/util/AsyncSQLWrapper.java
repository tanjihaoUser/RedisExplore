package com.wait.util;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;

import com.wait.entity.CacheSyncParam;
import com.wait.service.MQService;
import com.wait.sync.MethodExecutor;
import com.wait.util.message.CompensationMsg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class AsyncSQLWrapper {

    @Qualifier("thirdMQService")
    private final MQService mqService;

    @Qualifier("asyncSqlExecutor")
    private final ExecutorService executor;

    /**
     * 执行切面方法 - 用于策略类调用
     * 根据param中的isExecuteASync标志决定同步或异步执行
     * 
     * @param param          缓存参数
     * @param methodExecutor 方法执行器
     */
    public <T> void executeAspectMethod(CacheSyncParam<T> param, MethodExecutor methodExecutor) {
        boolean isVoidMethod = methodExecutor.isVoidMethod();

        // 创建Callable操作
        Callable<T> operation = () -> {
            try {
                Object result = methodExecutor.execute();
                @SuppressWarnings("unchecked")
                T typedResult = isVoidMethod ? null : (T) result;
                return typedResult;
            } catch (Throwable e) {
                throw new RuntimeException("Method execution failed", e);
            }
        };

        // 根据配置决定同步或异步执行
        if (Boolean.TRUE.equals(param.getIsExecuteASync())) {
            // 异步执行
            CompletableFuture<T> future = executeAsync(operation);
            future.thenAccept(result -> {
                if (!isVoidMethod && result != null) {
                    param.setNewValue(result);
                    log.debug("Async operation completed, result set for key: {}", param.getKey());
                }
            }).exceptionally(ex -> {
                log.error("Async operation failed: {}, send compensation message", param.getKey(), ex);
                Exception exception = ex instanceof Exception ? (Exception) ex
                        : new RuntimeException("Async operation failed", ex);
                sendToCompensationQueue(param, exception);
                return null;
            });
        } else {
            // 同步执行
            T result = executeSync(operation);
            if (!isVoidMethod && result != null) {
                param.setNewValue(result);
                log.debug("Sync operation completed, result set for key: {}", param.getKey());
            }
        }
    }

    /**
     * 带重试的执行逻辑
     */
    @Retryable(value = { SQLException.class, DataAccessException.class,
            RuntimeException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
    public <T> T executeWithRetry(Callable<T> operation, String key) throws Exception {
        RetryContext context = RetrySynchronizationManager.getContext();
        int retryCount = (context != null ? context.getRetryCount() : 0) + 1;
        log.info("Executing database operation, key: {}, attempt: {}", key, retryCount);
        return operation.call();
    }

    /**
     * 发送到补偿队列
     */
    private <T> void sendToCompensationQueue(CacheSyncParam<T> param, Exception error) {
        try {
            CompensationMsg<T> message = CompensationMsg.<T>builder()
                    .originalParam(param)
                    .failReason(error.getMessage())
                    .failTime(System.currentTimeMillis())
                    .build();
            mqService.sendDLMessage(param.getKey(), message);
        } catch (Exception mqError) {
            log.error("Failed to send to compensation queue: {}", param.getKey(), mqError);
        }
    }

    /**
     * 同步执行数据库操作（带重试）
     */
    public <T> T executeSync(Callable<T> operation) {
        try {
            return executeWithRetry(operation, "Sync Operation");
        } catch (Exception e) {
            log.error("Sync operation failed", e);
            throw new RuntimeException("Operation failed after retries", e);
        }
    }

    /**
     * 异步执行数据库操作（带重试和异常处理）
     */
    public <T> CompletableFuture<T> executeAsync(Callable<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeWithRetry(operation, "Async Operation");
            } catch (Exception e) {
                log.error("Async operation failed", e);
                throw new RuntimeException("Async operation failed after retries", e);
            }
        }, executor);
    }

    /**
     * 异步执行void数据库操作（带重试和异常处理）
     */
    public CompletableFuture<Void> executeAsyncVoid(Runnable operation) {
        return CompletableFuture.runAsync(() -> {
            try {
                executeWithRetry(() -> {
                    operation.run();
                    return null;
                }, "Async Void Operation");
            } catch (Exception e) {
                log.error("Async void operation failed", e);
                throw new RuntimeException("Async void operation failed after retries", e);
            }
        }, executor);
    }

    /**
     * 批量执行多个操作
     */
    public <T> List<T> executeBatch(List<Callable<T>> operations) {
        List<CompletableFuture<T>> futures = operations.stream()
                .map(this::executeAsync)
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

}