package com.wait.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.wait.entity.CacheSyncParam;
import com.wait.service.MQService;
import com.wait.util.message.CompensationMsg;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AsyncSQLWrapper {

    // 一个特殊的标记对象，用于标识void方法成功执行，但无需设置结果。使用一个静态实例避免创建过多对象。
    private static final VoidOperationResult VOID_OPERATION_RESULT = new VoidOperationResult();

    @Autowired
    @Qualifier("thirdMQService")
    private MQService mqService;

    private final ExecutorService executor = new ThreadPoolExecutor(
            5, 20, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadFactoryBuilder().setNameFormat("cache-pool-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 执行切面方法 - 统一入口使用一个内部包装方法，统一将Runnable和void方法转换为Callable。
     */
    public <T> void executeAspectMethod(CacheSyncParam<T> param, ProceedingJoinPoint joinPoint) {
        // 1. 判断方法类型
        boolean isVoidMethod = isVoidMethod(joinPoint);

        // 2. 创建统一的Callable操作
        Callable<Object> unifiedOperation = createUnifiedCallable(joinPoint, isVoidMethod);

        // 3. 记录是否需要设置结果
        boolean shouldSetResult = !isVoidMethod;

        // 4. 执行统一的操作
        executeUnifiedOperation(param, unifiedOperation, shouldSetResult);
    }

    /**
     * 创建统一的Callable，封装了原始方法的执行。对于void方法，返回一个标记值；对于有返回值的方法，返回实际结果。
     */
    private Callable<Object> createUnifiedCallable(ProceedingJoinPoint joinPoint, boolean isVoidMethod) {
        return () -> {
            try {
                Object result = joinPoint.proceed();
                if (isVoidMethod) {
                    log.debug("Void method executed successfully, returning marker object.");
                    // 返回标记对象，表示void方法成功执行
                    return VOID_OPERATION_RESULT;
                } else {
                    log.debug("Method with return value executed, result type: {}",
                            result != null ? result.getClass().getSimpleName() : "null");
                    return result;
                }
            } catch (Throwable e) {
                throw new RuntimeException("JoinPoint execution failed", e);
            }
        };
    }

    /**
     * 统一的操作执行核心方法
     * @param param 缓存参数
     * @param operation 统一的操作（总是Callable）
     * @param shouldSetResult 标志位，指示是否将操作结果设置到param中
     */
    @SuppressWarnings("unchecked")
    private <T> void executeUnifiedOperation(CacheSyncParam<T> param, Callable<Object> operation, boolean shouldSetResult) {
        // 将统一的Callable<Object>适配为Callable<T>，但实际执行逻辑由内部的lambda控制。
        // 我们通过shouldSetResult来决定是否设置结果。
        Callable<T> adaptedOperation = () -> {
            Object result = operation.call();
            // 如果本次操作是void方法，返回null给adaptedOperation，且外部根据shouldSetResult=false不会设置它。
            // 如果本次操作有返回值，则返回实际结果，外部会设置它。
            return (T) (result instanceof VoidOperationResult ? null : result);
        };

        if (Boolean.TRUE.equals(param.getIsExecuteASync())) {
            executeAsync(param, adaptedOperation, shouldSetResult);
        } else {
            executeSync(param, adaptedOperation, shouldSetResult);
        }
    }

    /**
     * 异步执行（统一逻辑）
     */
    private <T> void executeAsync(CacheSyncParam<T> param, Callable<T> operation, boolean shouldSetResult) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                T result = executeWithRetry(operation, param.getKey());
                // 根据标志位决定是否设置结果
                if (shouldSetResult) {
                    param.setNewValue(result);
                    log.debug("Async operation completed, result set for key: {}", param.getKey());
                } else {
                    log.debug("Async void operation completed for key: {}", param.getKey());
                    // 对于void操作，result为null，且我们不设置它，或者可以明确设置为null
                    // param.setResult(null); // 可选，保持param的洁净
                }
            } catch (Exception e) {
                log.error("Async operation failed: {}, send compensation message", param.getKey(), e);
                sendToCompensationQueue(param, e);
                throw new RuntimeException(e);
            }
        }, executor);

        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Async task completed with exception for key: {}", param.getKey(), throwable);
            } else {
                log.debug("Async task completed successfully for key: {}", param.getKey());
            }
        });
    }

    /**
     * 同步执行（统一逻辑）
     */
    private <T> void executeSync(CacheSyncParam<T> param, Callable<T> operation, boolean shouldSetResult) {
        try {
            T result = executeWithRetry(operation, param.getKey());
            // 根据标志位决定是否设置结果
            if (shouldSetResult) {
                param.setNewValue(result);
                log.debug("Sync operation completed, result set for key: {}", param.getKey());
            } else {
                log.debug("Sync void operation completed for key: {}", param.getKey());
                // param.setNewValue(null); // 可选
            }
        } catch (Exception e) {
            log.error("Sync operation failed: {}", param.getKey(), e);
            throw new RuntimeException("Operation failed after retries", e);
        }
    }

    /**
     * 带重试的执行逻辑（核心，无需修改）
     */
    @Retryable(
            value = {SQLException.class, DataAccessException.class, RuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000)
    )
    public <T> T executeWithRetry(Callable<T> operation, String key) throws Exception {
        RetryContext context = RetrySynchronizationManager.getContext();
        int retryCount = (context != null ? context.getRetryCount() : 0) + 1;
        log.info("Executing database operation, key: {}, attempt: {}", key, retryCount);
        return operation.call();
    }

    /**
     * 判断方法是否是void返回类型
     */
    private boolean isVoidMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getReturnType() == void.class;
    }

    /**
     * 发送到补偿队列（无需修改）
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
     * 简化调用 - 统一接口
     * 对外提供两个方法，但内部共用executeUnifiedOperation。
     */
    public <T> void executeSimple(String key, boolean async, Callable<T> operation) {
        CacheSyncParam<T> param = CacheSyncParam.<T>builder()
                .key(key)
                .isExecuteASync(async)
                .build();
        // 对于明确提供Callable的调用，总是需要设置结果
        executeUnifiedOperation(param, (Callable<Object>) operation, true);
    }

    public void executeSimpleVoid(String key, boolean async, Runnable operation) {
        CacheSyncParam<Void> param = CacheSyncParam.<Void>builder()
                .key(key)
                .isExecuteASync(async)
                .build();
        // 将Runnable包装成返回标记值的Callable，并指示不需要设置结果
        Callable<Object> unifiedOp = () -> {
            operation.run();
            return VOID_OPERATION_RESULT;
        };
        executeUnifiedOperation(param, unifiedOp, false);
    }

    /**
     * 批量执行多个操作（无需修改，因为它只处理Callable）
     */
    public <T> List<T> executeBatch(List<Callable<T>> operations) {
        List<CompletableFuture<T>> futures = operations.stream()
                .map(op -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return executeWithRetry(op, "Batch Operation");
                    } catch (Exception e) {
                        log.error("Batch operation failed", e);
                        throw new RuntimeException(e);
                    }
                }, executor))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    @PreDestroy
    public void destroy() {
        // ... 关闭线程池逻辑不变
    }

    /**
     * 一个私有静态内部类，作为void方法执行成功的标记。使用具体类而不是Object，是为了避免与任何有效的业务对象混淆。
     */
    private static final class VoidOperationResult {
        // 这个类没有内容，只作为类型标记。
    }
}