package com.wait.controller;

import com.wait.entity.domain.UserSession;
import com.wait.service.SessionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
        try {
            UserSession session = sessionService.createSession(request.getUsername(), request.getPassword());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sessionId", session.getSessionId());
            response.put("session", session);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 获取会话信息
     * GET /api/sessions/{sessionId}
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        try {
            UserSession session = sessionService.getSession(sessionId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", session);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 全量更新会话
     * PUT /api/sessions/{sessionId}
     */
    @PutMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> updateSession(
            @PathVariable String sessionId,
            @RequestBody UserSession sessionUpdate) {
        try {
            UserSession updatedSession = sessionService.updateSession(sessionId, sessionUpdate);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", updatedSession);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 部分更新会话字段
     * PATCH /api/sessions/{sessionId}
     */
    @PatchMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> partialUpdateSession(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> updates) {
        try {
            UserSession updatedSession = sessionService.partialUpdateSession(sessionId, updates);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", updatedSession);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 记录用户活动（高频操作，测试增量更新）
     * POST /api/sessions/{sessionId}/activity
     */
    @PostMapping("/{sessionId}/activity")
    public ResponseEntity<Map<String, Object>> recordActivity(@PathVariable String sessionId) {
        try {
            sessionService.recordActivity(sessionId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "活动记录成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 更新当前页面
     * PUT /api/sessions/{sessionId}/current-page
     */
    @PutMapping("/{sessionId}/current-page")
    public ResponseEntity<Map<String, Object>> updateCurrentPage(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {
        try {
            String currentPage = request.get("currentPage");
            sessionService.updateCurrentPage(sessionId, currentPage);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "页面更新成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 更新用户偏好
     * PUT /api/sessions/{sessionId}/preferences
     */
    @PutMapping("/{sessionId}/preferences")
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @PathVariable String sessionId,
            @RequestBody PreferenceRequest request) {
        try {
            sessionService.updatePreferences(sessionId, request.getTheme(), request.getLanguage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "偏好更新成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 设置会话属性
     * POST /api/sessions/{sessionId}/attributes
     */
    @PostMapping("/{sessionId}/attributes")
    public ResponseEntity<Map<String, Object>> setAttribute(
            @PathVariable String sessionId,
            @RequestBody AttributeRequest request) {
        try {
            sessionService.setAttribute(sessionId, request.getKey(), request.getValue());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "属性设置成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 获取会话属性
     * GET /api/sessions/{sessionId}/attributes/{key}
     */
    @GetMapping("/{sessionId}/attributes/{key}")
    public ResponseEntity<Map<String, Object>> getAttribute(
            @PathVariable String sessionId,
            @PathVariable String key) {
        try {
            Object value = sessionService.getAttribute(sessionId, key);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("key", key);
            response.put("value", value);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 登出并删除会话
     * DELETE /api/sessions/{sessionId}
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> logout(@PathVariable String sessionId) {
        try {
            sessionService.deleteSession(sessionId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "登出成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 验证会话有效性
     * GET /api/sessions/{sessionId}/validate
     */
    @GetMapping("/{sessionId}/validate")
    public ResponseEntity<Map<String, Object>> validateSession(@PathVariable String sessionId) {
        boolean isValid = sessionService.validateSession(sessionId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("valid", isValid);
        return ResponseEntity.ok(response);
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