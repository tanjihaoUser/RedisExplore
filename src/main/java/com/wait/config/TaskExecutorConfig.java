package com.wait.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行器和调度器配置
 * 统一管理所有线程池和任务调度器的配置
 */
@Configuration
@EnableAsync
@Slf4j
public class TaskExecutorConfig {

    private ExecutorService asyncSqlExecutorBean;

    /**
     * 重试专用的线程池
     */
    @Bean("retryExecutor")
    public ThreadPoolTaskExecutor retryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("retry-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 缓存操作专用的线程池
     */
    @Bean("cacheExecutor")
    public ThreadPoolTaskExecutor cacheExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("cache-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        // 优雅关闭配置
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * AsyncSQLWrapper 专用的线程池
     * 用于执行数据库操作的异步任务
     */
    @Bean("asyncSqlExecutor")
    public ExecutorService asyncSqlExecutor() {
        ExecutorService executor = new ThreadPoolExecutor(
                5, 20, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadFactoryBuilder().setNameFormat("cache-pool-%d").build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        // 保存引用以便在销毁时关闭
        this.asyncSqlExecutorBean = executor;
        return executor;
    }

    /**
     * 任务调度器 - 用于定时重试、延迟重试、定时刷新等调度任务
     * 用于写回策略（IncrementalWriteStrategy、SnapshotWriteStrategy）和定时刷新策略（ScheduledRefreshStrategy）
     */
    @Bean("refreshScheduler")
    public ThreadPoolTaskScheduler refreshScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("retry-scheduler-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 应用关闭时优雅关闭线程池
     */
    @PreDestroy
    public void destroy() {
        if (asyncSqlExecutorBean != null) {
            log.info("Shutting down asyncSqlExecutor thread pool...");
            asyncSqlExecutorBean.shutdown();
            try {
                // 等待现有任务完成，最多等待30秒
                if (!asyncSqlExecutorBean.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("asyncSqlExecutor did not terminate gracefully, forcing shutdown...");
                    asyncSqlExecutorBean.shutdownNow();
                    // 再等待5秒，如果还没完成就强制关闭
                    if (!asyncSqlExecutorBean.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("asyncSqlExecutor did not terminate after forced shutdown");
                    }
                }
                log.info("asyncSqlExecutor thread pool shutdown completed");
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for asyncSqlExecutor to shutdown", e);
                asyncSqlExecutorBean.shutdownNow();
                // 恢复中断状态
                Thread.currentThread().interrupt();
            }
        }
    }

}
