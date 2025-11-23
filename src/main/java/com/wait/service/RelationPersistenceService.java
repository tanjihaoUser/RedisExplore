package com.wait.service;

import java.util.concurrent.CompletableFuture;

/**
 * 关系数据持久化服务接口
 * 提供将 Redis Set 中的数据持久化到数据库的能力
 */
public interface RelationPersistenceService {

    /**
     * 持久化关注关系（Write-Through：立即写入数据库）
     */
    void persistFollow(Long followerId, Long followedId, boolean isFollow);

    /**
     * 持久化点赞关系（Write-Behind：异步批量写入数据库）
     * 
     * @return CompletableFuture 异步结果
     */
    CompletableFuture<Void> persistLike(Long userId, Long postId, boolean isLike);

    /**
     * 持久化收藏关系（Write-Behind：异步批量写入数据库）
     * 
     * @return CompletableFuture 异步结果
     */
    CompletableFuture<Void> persistFavorite(Long userId, Long postId, boolean isFavorite);

    /**
     * 持久化黑名单关系（Write-Through：立即写入数据库）
     */
    void persistBlock(Long userId, Long blockedUserId, boolean isBlock);

    /**
     * 批量同步关注关系到数据库（用于数据恢复/迁移）
     * 从 Redis Set 读取所有关注关系，批量写入数据库
     */
    void batchSyncFollows(Long userId);

    /**
     * 批量同步点赞关系到数据库（用于数据恢复/迁移）
     */
    void batchSyncLikes(Long postId);

    /**
     * 批量同步收藏关系到数据库（用于数据恢复/迁移）
     */
    void batchSyncFavorites(Long userId);
}
