package com.wait.service;

import com.wait.util.message.AsyncDataMsg;
import com.wait.util.message.CompensationMsg;

public interface MQService {

    void sendMessage(String topic, String key, AsyncDataMsg message);

    void sendDLMessage(String key, CompensationMsg message);

    void start();

    void shutdown();

    int getQueueSize();

    boolean isHealthy();
}