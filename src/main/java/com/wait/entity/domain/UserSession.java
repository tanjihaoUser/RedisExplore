package com.wait.entity.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户会话实体 - 设计为与Redis Hash字段对应
 * 字段命名尽量简单，方便直接作为Hash的field
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserSession implements Serializable {

    // ===== 基础身份信息 =====
    private String sessionId;           // 会话ID (Hash Key)
    private String userId;       // 用户ID
    private String username;     // 用户名

    // ===== 高频更新字段 (用于测试Write-Behind) =====
    private Long lastActiveTime; // 最后活跃时间
    private String currentPage;  // 当前页面
    private Integer visitCount;  // 访问次数统计

    // ===== 低频更新字段 (用于测试普通更新) =====
    private String theme;        // 主题偏好
    private String language;     // 语言偏好

    // 临时扩展属性（不持久化到Redis）
    @Builder.Default
    private transient Map<String, Object> attributes = new HashMap<>();

    public UserSession(String id, String userId, String username) {
        this.sessionId = id;
        this.userId = userId;
        this.username = username;
        this.lastActiveTime = System.currentTimeMillis();
        this.visitCount = 0;
        this.theme = "light";
        this.language = "zh-CN";
    }

    /**
     * 增加访问计数 - 高频操作示例
     */
    public void incrementVisit() {
        this.visitCount++;
        this.lastActiveTime = System.currentTimeMillis();
    }

    /**
     * 获取扩展属性
     */
    public Object getAttribute(String key) {
        return attributes != null ? attributes.get(key) : null;
    }

    /**
     * 设置扩展属性
     */
    public void setAttribute(String key, Object value) {
        if (attributes == null) {
            attributes = new ConcurrentHashMap<>();
        }
        attributes.put(key, value);
    }
}
