package com.wait.controller;

import com.wait.entity.domain.UserSession;
import com.wait.service.SessionService;
import com.wait.util.ResponseUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 使用Redis中的hash存储session信息
 * */
@Slf4j
@RestController
@RequestMapping("/sessions")
public class SessionController {

    @Autowired
    private SessionService sessionService;

    /**
     * 登录并创建会话
     * POST /api/sessions/login
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
            UserSession session = sessionService.createSession(request.getUsername(), request.getPassword());

        Map<String, Object> extraFields = new HashMap<>();
        extraFields.put("sessionId", session.getSessionId());
        return ResponseUtil.success(extraFields, session);
    }

    /**
     * 获取会话信息
     * GET /api/sessions/{sessionId}
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
            UserSession session = sessionService.getSession(sessionId);
        return ResponseUtil.success(session);
    }

    /**
     * 全量更新会话
     * PUT /api/sessions/{sessionId}
     */
    @PutMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> updateSession(
            @PathVariable String sessionId,
            @RequestBody UserSession sessionUpdate) {
            UserSession updatedSession = sessionService.updateSession(sessionId, sessionUpdate);
        return ResponseUtil.success(updatedSession);
    }

    /**
     * 部分更新会话字段
     * PATCH /api/sessions/{sessionId}
     */
    @PatchMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> partialUpdateSession(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> updates) {
            UserSession updatedSession = sessionService.partialUpdateSession(sessionId, updates);
        return ResponseUtil.success(updatedSession);
    }

    /**
     * 记录用户活动（高频操作，测试增量更新）
     * POST /api/sessions/{sessionId}/activity
     */
    @PostMapping("/{sessionId}/activity")
    public ResponseEntity<Map<String, Object>> recordActivity(@PathVariable String sessionId) {
            sessionService.recordActivity(sessionId);
        return ResponseUtil.success("活动记录成功");
    }

    /**
     * 更新当前页面
     * PUT /api/sessions/{sessionId}/current-page
     */
    @PutMapping("/{sessionId}/current-page")
    public ResponseEntity<Map<String, Object>> updateCurrentPage(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {
            String currentPage = request.get("currentPage");
            sessionService.updateCurrentPage(sessionId, currentPage);
        return ResponseUtil.success("页面更新成功");
    }

    /**
     * 更新用户偏好
     * PUT /api/sessions/{sessionId}/preferences
     */
    @PutMapping("/{sessionId}/preferences")
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @PathVariable String sessionId,
            @RequestBody PreferenceRequest request) {
            sessionService.updatePreferences(sessionId, request.getTheme(), request.getLanguage());
        return ResponseUtil.success("偏好更新成功");
    }

    /**
     * 设置会话属性
     * POST /api/sessions/{sessionId}/attributes
     */
    @PostMapping("/{sessionId}/attributes")
    public ResponseEntity<Map<String, Object>> setAttribute(
            @PathVariable String sessionId,
            @RequestBody AttributeRequest request) {
            sessionService.setAttribute(sessionId, request.getKey(), request.getValue());
        return ResponseUtil.success("属性设置成功");
    }

    /**
     * 获取会话属性
     * GET /api/sessions/{sessionId}/attributes/{key}
     */
    @GetMapping("/{sessionId}/attributes/{key}")
    public ResponseEntity<Map<String, Object>> getAttribute(
            @PathVariable String sessionId,
            @PathVariable String key) {
            Object value = sessionService.getAttribute(sessionId, key);
        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("value", value);
        return ResponseUtil.success(data);
    }

    /**
     * 登出并删除会话
     * DELETE /api/sessions/{sessionId}
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> logout(@PathVariable String sessionId) {
            sessionService.deleteSession(sessionId);
        return ResponseUtil.success("登出成功");
    }

    /**
     * 验证会话有效性
     * GET /api/sessions/{sessionId}/validate
     */
    @GetMapping("/{sessionId}/validate")
    public ResponseEntity<Map<String, Object>> validateSession(@PathVariable String sessionId) {
        boolean isValid = sessionService.validateSession(sessionId);
        Map<String, Object> data = new HashMap<>();
        data.put("valid", isValid);
        return ResponseUtil.success(data);
    }

    // 请求对象定义
    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class PreferenceRequest {
        private String theme;
        private String language;
    }

    @Data
    public static class AttributeRequest {
        private String key;
        private Object value;
    }
}