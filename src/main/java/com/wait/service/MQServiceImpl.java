package com.wait.service;

import com.wait.util.message.CompensationMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MQServiceImpl {

    public static final String COMPENSATION_TOPIC = "cache-compensation-topic";
    public static final String DL_TOPIC = "deadline-topic";
    public static final String TOPIC_SUFFIX = "related-topic";
    public static final long SEND_TIMEOUT_MS = 3000;

    public void sendMessage(String topic, String key, Object message) {
        // 发送消息到MQ
        log.info("send msg to mq, topic: {}, key: {}, message: {}", topic, key, message);
    }

    public void sendDLMessage(String key, CompensationMsg message) {
        // 发送消息到死信队列
        log.info("send msg to deadline topic, topic: {}, key: {}, message: {}", DL_TOPIC, key, message);
    }



}
