package com.wait.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关系服务接口 - 用于处理关注、点赞、收藏等社交媒体关系
 */
public interface RelationService {

    // ==================== 关注相关 ====================

    /**
     * 关注用户
     */
    boolean follow(Long followerId, Long followedId);

    /**
     * 取消关注
     */
    boolean unfollow(Long followerId, Long followedId);

    /**
     * 检查是否关注
     */
    boolean isFollowing(Long followerId, Long followedId);

    /**
     * 获取关注列表
     */
    Set<Long> getFollowing(Long userId);

    /**
     * 获取粉丝列表
     */
    Set<Long> getFollowers(Long userId);

    /**
     * 获取关注数
     */
    Long getFollowingCount(Long userId);

    /**
     * 获取粉丝数
     */
    Long getFollowerCount(Long userId);

    /**
     * 获取共同关注
     */
    Set<Long> getMutualFollowing(Long userId1, Long userId2);

    /**
     * 检查是否互相关注
     */
    boolean isMutualFollowing(Long userId1, Long userId2);

    // ==================== 点赞相关 ====================

    /**
     * 点赞帖子
     */
    boolean likePost(Long userId, Long postId);

    /**
     * 取消点赞
     */
    boolean unlikePost(Long userId, Long postId);

    /**
     * 检查是否已点赞
     */
    boolean isLiked(Long userId, Long postId);

    /**
     * 获取点赞用户列表
     */
    Set<Long> getLikers(Long postId);

    /**
     * 获取点赞数
     */
    Long getLikeCount(Long postId);

    /**
     * 批量检查点赞状态
     */
    Map<Long, Boolean> batchCheckLiked(Long userId, List<Long> postIds);

    /**
     * 获取用户点赞过的帖子列表
     */
    Set<Long> getUserLikedPosts(Long userId);

    // ==================== 收藏相关 ====================

    /**
     * 收藏帖子
     */
    boolean favoritePost(Long userId, Long postId);

    /**
     * 取消收藏
     */
    boolean unfavoritePost(Long userId, Long postId);

    /**
     * 检查是否已收藏
     */
    boolean isFavorited(Long userId, Long postId);

    /**
     * 获取用户收藏列表
     */
    Set<Long> getUserFavorites(Long userId);

    /**
     * 获取收藏数
     */
    Long getFavoriteCount(Long postId);

    // ==================== 黑名单相关 ====================

    /**
     * 拉黑用户
     */
    boolean blockUser(Long userId, Long blockedUserId);

    /**
     * 取消拉黑
     */
    boolean unblockUser(Long userId, Long blockedUserId);

    /**
     * 检查是否在黑名单中
     */
    boolean isBlocked(Long userId, Long blockedUserId);

    /**
     * 获取黑名单列表
     */
    Set<Long> getBlacklist(Long userId);

    /**
     * 过滤黑名单用户
     */
    List<Long> filterBlacklisted(Long userId, List<Long> userIds);
}
