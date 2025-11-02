package com.wait.service.impl;

import com.wait.entity.domain.UserSession;
import com.wait.mapper.UserSessionMapper;
import com.wait.service.SessionService;
import com.wait.util.instance.HashMappingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SessionServiceImpl implements SessionService {

    @Autowired
    private UserSessionMapper userSessionMapper;

    @Autowired
    private HashMappingUtil hashMappingUtil;

    @Override
    public UserSession createSession(String username, String password) {

        String sessionId = generateSessionId();

        UserSession session = new UserSession(sessionId, "1001", username, System.currentTimeMillis(),
                "/home", 1, "light", "zh-CN", new HashMap<>());

        userSessionMapper.insert(session);

        log.info("created session: {}", sessionId);
        return session;
    }

    @Override
    public UserSession getSession(String sessionId) {
        UserSession session = userSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在或已过期");
        }
        return session;
    }

    @Override
    public UserSession updateSession(String sessionId, UserSession sessionUpdate) {
        log.debug("trying to get session, id: {}", sessionId);
        UserSession existingSession = getSession(sessionId);
        log.debug("got session: {}", existingSession);

        // 将更新对象转换为Map，排除不需要更新的字段
        Map<String, Object> updateMap = hashMappingUtil.objectToMap(sessionUpdate).entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));;

        // 移除不需要更新的字段
        updateMap.remove("sessionId"); // 保持sessionId不变

        // 将Map应用到现有对象
        UserSession updateSession = applyUpdatesToSession(existingSession, updateMap);
        log.debug("updated session: {}", updateSession);

        userSessionMapper.update(updateSession);
        log.info("update session totally: {}", sessionId);

        return updateSession;
    }

    @Override
    public UserSession partialUpdateSession(String sessionId, Map<String, Object> updates) {
        UserSession existingSession = getSession(sessionId);

        // 过滤掉空值和无效字段
        Map<String, Object> validUpdates = filterValidUpdates(updates);

        if (!validUpdates.isEmpty()) {
            UserSession updateSession = applyUpdatesToSession(existingSession, validUpdates);
            existingSession.setLastActiveTime(System.currentTimeMillis());

            userSessionMapper.update(updateSession);
            log.info("update session partially: {} properties: {}", sessionId, validUpdates.keySet());
            return updateSession;
        }

        return existingSession;
    }

    @Override
    public void recordActivity(String sessionId) {
        long currentTime = System.currentTimeMillis();

        // 使用增量更新，性能更好
        userSessionMapper.incrementVisitAndUpdateTime(sessionId, currentTime, 1);
        log.debug("record user activity: {}", sessionId);
    }

    @Override
    public void updateCurrentPage(String sessionId, String currentPage) {
        if (StringUtils.hasText(currentPage)) {
            userSessionMapper.updateCurrentPage(sessionId, currentPage);
            userSessionMapper.updateLastActiveTime(sessionId, System.currentTimeMillis());
            log.debug("update current page: {} -> {}", sessionId, currentPage);
        }
    }

    @Override
    public void updatePreferences(String sessionId, String theme, String language) {
        if (StringUtils.hasText(theme) || StringUtils.hasText(language)) {
            userSessionMapper.updatePreference(sessionId, theme, language);
            userSessionMapper.updateLastActiveTime(sessionId, System.currentTimeMillis());
            log.info("update preferences: {} -> theme={}, language={}", sessionId, theme, language);
        }
    }

    @Override
    public void setAttribute(String sessionId, String key, Object value) {
        UserSession session = getSession(sessionId);

        // 使用Map操作属性
        Map<String, Object> attributes = session.getAttributes();
        if (attributes == null) {
            attributes = new HashMap<>();
            session.setAttributes(attributes);
        }

        attributes.put(key, value);
        session.setLastActiveTime(System.currentTimeMillis());

        userSessionMapper.update(session);
        log.debug("set attribute: {} -> {}={}", sessionId, key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String sessionId, String key) {
        UserSession session = getSession(sessionId);
        if (session.getAttributes() != null) {
            return (T) session.getAttributes().get(key);
        }
        return null;
    }

    @Override
    public void deleteSession(String sessionId) {
        userSessionMapper.deleteById(sessionId);
        log.info("delete session: {}", sessionId);
    }

    @Override
    public boolean validateSession(String sessionId) {
        try {
            UserSession session = userSessionMapper.selectById(sessionId);
            return session != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将更新应用到Session对象
     */
    private UserSession applyUpdatesToSession(UserSession session, Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return session;
        }

        try {
            log.debug("session param: {}, updateMap: {}", session, updates);
            // 使用HashMappingUtil进行智能映射
            Map<String, Object> sessionMap = hashMappingUtil.objectToMap(session);
            sessionMap.putAll(updates); // 合并更新

            log.debug("after trans, sessionMap: {}", sessionMap);
            // 转换回对象
            UserSession updatedSession = hashMappingUtil.mapToObject(sessionMap, UserSession.class);
            log.debug("after trans, updatedSession: {}", updatedSession);
            updatedSession.setAttributes(new HashMap<>(session.getAttributes()));
            return updatedSession;

        } catch (Exception e) {
            log.warn("Failed to apply updates to session: {}", e.getMessage());
        }
        return session;
    }

    /**
     * 复制Session属性（保持对象引用不变）
     */
    private void copySessionProperties(UserSession target, UserSession source) {
        target.setUserId(source.getUserId());
        target.setUsername(source.getUsername());
        target.setLastActiveTime(source.getLastActiveTime());
        target.setVisitCount(source.getVisitCount());
        target.setCurrentPage(source.getCurrentPage());
        target.setTheme(source.getTheme());
        target.setLanguage(source.getLanguage());

        // 深度复制attributes
        if (source.getAttributes() != null) {
            target.setAttributes(new HashMap<>(source.getAttributes()));
        } else {
            target.setAttributes(null);
        }
    }

    /**
     * 过滤有效的更新字段
     */
    private Map<String, Object> filterValidUpdates(Map<String, Object> updates) {
        Map<String, Object> validUpdates = new HashMap<>();
        String[] validFields = {"userId", "username", "currentPage", "theme", "language", "attributes"};

        for (String field : validFields) {
            if (updates.containsKey(field) && updates.get(field) != null) {
                validUpdates.put(field, updates.get(field));
            }
        }

        return validUpdates;
    }

    private String generateSessionId() {
        return "sess_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
}