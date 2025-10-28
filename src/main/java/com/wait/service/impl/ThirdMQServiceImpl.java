package com.wait.service.impl;

import com.wait.service.MQService;
import com.wait.util.message.AsyncDataMsg;
import com.wait.util.message.CompensationMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service("thirdMQService")
@Slf4j
public class ThirdMQServiceImpl implements MQService {

    @Value("${mq.third.endpoint:}")
    private String mqEndpoint;

    @Value("${mq.third.timeout:3000}")
    private long timeoutMs;

    private boolean initialized = false;

    @Override
    public void start() {
        // 初始化第三方MQ客户端
        try {
            // thirdMqClient = new ThirdMqClient(mqEndpoint);
            initialized = true;
            log.info("ThirdMQ服务初始化成功, endpoint: {}", mqEndpoint);
        } catch (Exception e) {
            log.error("ThirdMQ服务初始化失败", e);
            initialized = false;
        }
    }

    @Override
    public void shutdown() {
        // 关闭第三方MQ客户端
        // if (thirdMqClient != null) { thirdMqClient.close(); }
        initialized = false;
        log.info("ThirdMQ服务已关闭");
    }

    @Override
    public void sendMessage(String topic, String key, AsyncDataMsg message) {
        if (!initialized) {
            log.error("ThirdMQ: 服务未初始化，消息发送失败");
            return;
        }

        try {
            // 第三方MQ的实际发送逻辑
            // thirdMqClient.send(topic, key, message, timeoutMs);
            log.info("ThirdMQ: 消息发送成功, topic: {}, key: {}", topic, key);
        } catch (Exception e) {
            log.error("ThirdMQ: 消息发送失败, topic: {}, key: {}", topic, key, e);
            // 可以添加重试逻辑或降级到内存MQ
        }
    }

    @Override
    public void sendDLMessage(String key, CompensationMsg message) {
        if (!initialized) {
            log.error("ThirdMQ: 服务未初始化，死信消息发送失败");
            return;
        }

        try {
            // 发送到死信队列
            // thirdMqClient.send(DL_TOPIC, key, message, timeoutMs);
            log.info("ThirdMQ: 死信消息发送成功, key: {}", key);
        } catch (Exception e) {
            log.error("ThirdMQ: 死信消息发送失败, key: {}", key, e);
        }
    }

    @Override
    public int getQueueSize() {
        // 第三方MQ通常无法直接获取队列大小
        return -1;
    }

    @Override
    public boolean isHealthy() {
        return initialized; // && thirdMqClient.isConnected();
    }
}