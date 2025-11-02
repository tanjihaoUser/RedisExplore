package com.wait.service;

import com.wait.util.message.AsyncDataMsg;
import com.wait.util.message.CompensationMsg;

public interface MQService {

    public static final String COMPENSATION_TOPIC = "cache-compensation-topic";
    public static final String DL_TOPIC = "deadline-topic";
    public static final String TOPIC_SUFFIX = "related-topic";
    public static final long SEND_TIMEOUT_MS = 3000;

    void sendMessage(String topic, String key, AsyncDataMsg message);

    void sendDLMessage(String key, CompensationMsg message);

    void start();

    void shutdown();

    int getQueueSize();

    boolean isHealthy();
}