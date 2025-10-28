package com.wait.service.impl;

import com.wait.service.MQService;
import com.wait.util.message.AsyncDataMsg;
import com.wait.util.message.CompensationMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service("memoryMQService")
@Slf4j
public class MemoryMQServiceImpl implements MQService {

    // 内存消息队列
    private final BlockingQueue<MQMessage> messageQueue = new LinkedBlockingQueue<>(10000);
    private final BlockingQueue<MQMessage> dlqQueue = new LinkedBlockingQueue<>(5000);

    // 消费线程池
    private final ExecutorService consumerExecutor = Executors.newFixedThreadPool(2,
            r -> new Thread(r, "MemoryMQ-Consumer"));

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "MemoryMQ-Scheduler")
    );

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger queueSize = new AtomicInteger(0);

    // 消息处理器（由业务方注入）
    private MessageHandler messageHandler;

    @PostConstruct
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            // 启动普通消息消费者
            consumerExecutor.execute(this::consumeMessages);
            // 启动死信队列消费者
            consumerExecutor.execute(this::consumeDLQMessages);
            // 启动监控任务
            scheduler.scheduleAtFixedRate(this::monitorQueue, 1, 1, TimeUnit.MINUTES);

            log.info("MemoryMQ服务启动成功");
        }
    }

    @PreDestroy
    @Override
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            consumerExecutor.shutdown();
            scheduler.shutdown();
            try {
                if (!consumerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    consumerExecutor.shutdownNow();
                }
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("MemoryMQ服务已关闭");
        }
    }

    @Override
    public void sendMessage(String topic, String key, AsyncDataMsg message) {
        MQMessage mqMessage = new MQMessage(topic, key, message, System.currentTimeMillis());
        try {
            boolean offered = messageQueue.offer(mqMessage, 100, TimeUnit.MILLISECONDS);
            if (offered) {
                queueSize.incrementAndGet();
                log.debug("MemoryMQ: 消息发送成功, topic: {}, key: {}", topic, key);
            } else {
                log.warn("MemoryMQ: 消息队列已满，消息被丢弃, topic: {}, key: {}", topic, key);
                // 队列满时可以考虑降级策略
                handleQueueFull(mqMessage);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("MemoryMQ: 消息发送被中断, topic: {}, key: {}", topic, key, e);
        }
    }

    @Override
    public void sendDLMessage(String key, CompensationMsg message) {
        MQMessage dlqMessage = new MQMessage(DL_TOPIC, key, message, System.currentTimeMillis());
        try {
            boolean offered = dlqQueue.offer(dlqMessage, 100, TimeUnit.MILLISECONDS);
            if (offered) {
                log.debug("MemoryMQ: 死信消息发送成功, key: {}", key);
            } else {
                log.error("MemoryMQ: 死信队列已满，消息丢失, key: {}", key);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("MemoryMQ: 死信消息发送被中断, key: {}", key, e);
        }
    }

    @Override
    public int getQueueSize() {
        return queueSize.get();
    }

    @Override
    public boolean isHealthy() {
        return running.get() &&
                messageQueue.remainingCapacity() > 1000 &&
                dlqQueue.remainingCapacity() > 500;
    }

    /**
     * 消费普通消息
     */
    private void consumeMessages() {
        while (running.get() || !messageQueue.isEmpty()) {
            try {
                MQMessage message = messageQueue.poll(1, TimeUnit.SECONDS);
                if (message != null) {
                    processMessage(message);
                    queueSize.decrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("MemoryMQ: 消息消费异常", e);
            }
        }
    }

    /**
     * 消费死信队列消息
     */
    private void consumeDLQMessages() {
        while (running.get() || !dlqQueue.isEmpty()) {
            try {
                MQMessage dlqMessage = dlqQueue.poll(1, TimeUnit.SECONDS);
                if (dlqMessage != null) {
                    processDLQMessage(dlqMessage);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("MemoryMQ: 死信消息消费异常", e);
            }
        }
    }

    /**
     * 处理普通消息
     */
    private void processMessage(MQMessage message) {
        try {
            if (messageHandler != null) {
                messageHandler.handleMessage(message.topic, message.key, message.message);
            } else {
                log.warn("MemoryMQ: 未设置消息处理器，消息被忽略, topic: {}, key: {}",
                        message.topic, message.key);
            }
        } catch (Exception e) {
            log.error("MemoryMQ: 消息处理失败, topic: {}, key: {}",
                    message.topic, message.key, e);
            // 处理失败的消息进入死信队列
            CompensationMsg compensationMsg = new CompensationMsg(
                    extractParamFromMessage(message),
                    e.getMessage(),
                    System.currentTimeMillis()
            );
            sendDLMessage(message.key, compensationMsg);
        }
    }

    /**
     * 处理死信队列消息
     */
    private void processDLQMessage(MQMessage dlqMessage) {
        log.error("MemoryMQ: 处理死信消息, key: {}, message: {}",
                dlqMessage.key, dlqMessage.message);
        // 这里可以实现死信消息的持久化、告警等逻辑
        // persistenceService.saveDeadLetter(dlqMessage);
        // alertService.sendAlert("死信消息告警", dlqMessage.toString());
    }

    /**
     * 监控队列状态
     */
    private void monitorQueue() {
        int size = messageQueue.size();
        int dlqSize = dlqQueue.size();
        int remaining = messageQueue.remainingCapacity();

        if (size > 8000) {
            log.warn("MemoryMQ: 消息队列使用率过高, 当前大小: {}, 剩余容量: {}", size, remaining);
        }

        if (dlqSize > 1000) {
            log.error("MemoryMQ: 死信队列堆积严重, 当前大小: {}", dlqSize);
        }

        log.debug("MemoryMQ状态 - 普通队列: {}/{}, 死信队列: {}/{}",
                size, size + remaining, dlqSize, dlqQueue.remainingCapacity() + dlqSize);
    }

    /**
     * 处理队列满的情况
     */
    private void handleQueueFull(MQMessage message) {
        // 可以实现的降级策略：
        // 1. 记录到本地文件
        // 2. 发送到外部存储
        // 3. 直接拒绝并抛出异常
        log.error("MemoryMQ: 队列已满，消息被拒绝, topic: {}, key: {}",
                message.topic, message.key);
    }

    /**
     * 设置消息处理器
     */
    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    private Object extractParamFromMessage(MQMessage message) {
        // 从消息中提取参数的逻辑
        if (message.message instanceof AsyncDataMsg) {
            return ((AsyncDataMsg<?>) message.message).getData();
        }
        return null;
    }

    // 内部消息类
    private static class MQMessage {
        final String topic;
        final String key;
        final Object message;
        final long timestamp;

        MQMessage(String topic, String key, Object message, long timestamp) {
            this.topic = topic;
            this.key = key;
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    // 消息处理器接口
    public interface MessageHandler {
        void handleMessage(String topic, String key, Object message);
    }
}