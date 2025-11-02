package com.wait.service;


import com.wait.entity.domain.UserSession;

import java.util.Map;

public interface SessionService {

    /**
     * 创建新会话（登录）
     */
    UserSession createSession(String username, String password);

    /**
     * 根据ID获取会话
     */
    UserSession getSession(String sessionId);

    /**
     * 全量更新会话信息
     */
    UserSession updateSession(String sessionId, UserSession sessionUpdate);

    /**
     * 部分更新会话字段
     */
    UserSession partialUpdateSession(String sessionId, Map<String, Object> updates);

    /**
     * 记录用户活动（更新最后活跃时间和访问次数）
     */
    void recordActivity(String sessionId);

    /**
     * 更新当前页面
     */
    void updateCurrentPage(String sessionId, String currentPage);

    /**
     * 更新用户偏好（主题和语言）
     */
    void updatePreferences(String sessionId, String theme, String language);

    /**
     * 设置会话属性
     */
    void setAttribute(String sessionId, String key, Object value);

    /**
     * 获取会话属性
     */
    <T> T getAttribute(String sessionId, String key);

    /**
     * 删除会话（登出）
     */
    void deleteSession(String sessionId);

    /**
     * 验证会话有效性
     */
    boolean validateSession(String sessionId);
}