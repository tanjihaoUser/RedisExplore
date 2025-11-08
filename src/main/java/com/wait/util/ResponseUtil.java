package com.wait.util;

import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * 响应工具类 - 统一构建API响应格式
 */
public class ResponseUtil {

    /**
     * 构建成功响应
     */
    public static ResponseEntity<Map<String, Object>> success(Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        if (data != null) {
            response.put("data", data);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 构建成功响应（带消息）
     */
    public static ResponseEntity<Map<String, Object>> success(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        if (data != null) {
            response.put("data", data);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 构建成功响应（仅消息）
     */
    public static ResponseEntity<Map<String, Object>> success(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    /**
     * 构建带额外字段的成功响应
     */
    public static ResponseEntity<Map<String, Object>> success(Map<String, Object> extraFields, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        if (data != null) {
            response.put("data", data);
        }
        if (extraFields != null) {
            response.putAll(extraFields);
        }
        return ResponseEntity.ok(response);
    }
}

