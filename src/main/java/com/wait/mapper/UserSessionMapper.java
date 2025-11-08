package com.wait.mapper;

import com.wait.annotation.RedisCache;
import com.wait.entity.domain.UserSession;
import com.wait.entity.type.CacheType;
import com.wait.entity.type.DataOperationType;
import com.wait.entity.type.ReadStrategyType;
import com.wait.entity.type.WriteStrategyType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserSessionMapper {

    // ==================== 全量更新操作 ====================
    /**
     * 插入一条新的Session记录（全量插入）
     */
    @RedisCache(prefix = "user:session", key = "#userSession.sessionId", expire = 300,
                cacheType = CacheType.HASH, returnType = UserSession.class,
                operation = DataOperationType.UPDATE, writeStrategy = WriteStrategyType.WRITE_THROUGH)
    int insert(UserSession userSession);

    /**
     * 全量更新Session信息（快照策略使用）
     */
    @RedisCache(prefix = "user:session", key = "#userSession.sessionId", expire = 300,
                cacheType = CacheType.HASH, returnType = UserSession.class,
                operation = DataOperationType.UPDATE, writeStrategy = WriteStrategyType.SNAPSHOT_WRITE_BEHIND)
    int update(UserSession userSession);

    /**
     * 根据主键查询完整的Session信息
     */
    @RedisCache(prefix = "user:session", key = "#sessionId", expire = 300,
            cacheType = CacheType.HASH, returnType = UserSession.class,
            operation = DataOperationType.SELECT, readStrategy = ReadStrategyType.LAZY_LOAD)
    UserSession selectById(String sessionId);

    // ==================== 增量更新操作 ====================
    /**
     * 增量更新最后活跃时间和访问次数
     */
    @RedisCache(prefix = "user:session", key = "#sessionId", expire = 300,
            cacheType = CacheType.HASH, returnType = UserSession.class,
            operation = DataOperationType.UPDATE, writeStrategy = WriteStrategyType.INCREMENTAL_WRITE_BEHIND)
    int incrementVisitAndUpdateTime(@Param("sessionId") String sessionId,
                                    @Param("lastActiveTime") Long lastActiveTime,
                                    @Param("visitCount") Integer incrementCount);

    /**
     * 只更新最后活跃时间（高频字段）
     */
    int updateLastActiveTime(@Param("sessionId") String sessionId, @Param("lastActiveTime") Long lastActiveTime);

    /**
     * 只更新访问次数（增量累加）
     */
    int incrementVisitCount(@Param("sessionId") String sessionId, @Param("visitCount") Integer increment);

    /**
     * 更新当前页面（中频字段）
     */
    int updateCurrentPage(@Param("sessionId") String sessionId, @Param("currentPage") String currentPage);

    /**
     * 更新主题和语言（低频字段）
     */
    @RedisCache(prefix = "user:session", key = "#sessionId", expire = 300,
                cacheType = CacheType.HASH, returnType = UserSession.class,
                operation = DataOperationType.UPDATE, writeStrategy = WriteStrategyType.CACHE_ASIDE)
    int updatePreference(@Param("sessionId") String sessionId, @Param("theme") String theme, @Param("language") String language);

    // ==================== 工具操作 ====================
    /**
     * 删除Session（登出时使用）
     */
    int deleteById(String sessionId);

    /**
     * 清理过期Session（可由定时任务调用）
     */
    int deleteExpiredSessions(@Param("timestamp") Long timestamp);
}