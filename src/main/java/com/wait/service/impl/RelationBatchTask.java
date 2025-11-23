package com.wait.service.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关系数据批量持久化任务
 * 用于缓冲点赞和收藏操作，支持定时和定量批量写入
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationBatchTask {

    /**
     * 点赞操作缓冲：key = "postId:userId", value = true(点赞) / false(取消点赞)
     */
    @Default
    private ConcurrentMap<String, Boolean> likeOperations = new ConcurrentHashMap<>();

    /**
     * 收藏操作缓冲：key = "userId:postId", value = true(收藏) / false(取消收藏)
     */
    @Default
    private ConcurrentMap<String, Boolean> favoriteOperations = new ConcurrentHashMap<>();

    /**
     * 添加点赞操作到缓冲
     */
    public void addLikeOperation(Long postId, Long userId, boolean isLike) {
        String key = postId + ":" + userId;
        likeOperations.put(key, isLike);
    }

    /**
     * 添加收藏操作到缓冲
     */
    public void addFavoriteOperation(Long userId, Long postId, boolean isFavorite) {
        String key = userId + ":" + postId;
        favoriteOperations.put(key, isFavorite);
    }

    /**
     * 获取点赞操作数量
     */
    public int getLikeOperationCount() {
        return likeOperations.size();
    }

    /**
     * 获取收藏操作数量
     */
    public int getFavoriteOperationCount() {
        return favoriteOperations.size();
    }

    /**
     * 获取总操作数量
     */
    public int getTotalOperationCount() {
        return likeOperations.size() + favoriteOperations.size();
    }

    /**
     * 清空所有操作
     */
    public void clear() {
        likeOperations.clear();
        favoriteOperations.clear();
    }

    /**
     * 检查是否有待处理的操作
     */
    public boolean hasPendingOperations() {
        return !likeOperations.isEmpty() || !favoriteOperations.isEmpty();
    }
}
