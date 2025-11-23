package com.wait.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wait.entity.param.BatchCheckRequest;
import com.wait.entity.param.BlockRequest;
import com.wait.entity.param.FavoriteRequest;
import com.wait.entity.param.FilterRequest;
import com.wait.entity.param.FollowRequest;
import com.wait.entity.param.LikeRequest;
import com.wait.service.RelationService;
import com.wait.util.ResponseUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 关系控制器 - 处理关注、点赞、收藏、黑名单等社交媒体关系
 * 使用 Redis Set 数据结构实现
 */
@Slf4j
@RestController
@RequestMapping("/relation")
@RequiredArgsConstructor
public class RelationController {

    private final RelationService relationService;

    // ==================== 关注相关 ====================

    /**
     * 关注用户
     * POST /relation/follow
     */
    @PostMapping("/follow")
    public ResponseEntity<Map<String, Object>> follow(@RequestBody FollowRequest request) {
        boolean success = relationService.follow(request.getFollowerId(), request.getFollowedId());

        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("followerId", request.getFollowerId());
        data.put("followedId", request.getFollowedId());

        if (success) {
            return ResponseUtil.success("关注成功", data);
        } else {
            return ResponseUtil.success("已经关注过了", data);
        }
    }

    /**
     * 取消关注
     * DELETE /relation/follow
     */
    @DeleteMapping("/follow")
    public ResponseEntity<Map<String, Object>> unfollow(@RequestParam Long followerId, @RequestParam Long followedId) {
        boolean success = relationService.unfollow(followerId, followedId);

        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("followerId", followerId);
        data.put("followedId", followedId);

        return ResponseUtil.success(success ? "取消关注成功" : "未关注该用户", data);
    }

    /**
     * 检查是否关注
     * GET /relation/follow/check
     */
    @GetMapping("/follow/check")
    public ResponseEntity<Map<String, Object>> checkFollowing(
            @RequestParam Long followerId,
            @RequestParam Long followedId) {
        boolean isFollowing = relationService.isFollowing(followerId, followedId);

        Map<String, Object> data = new HashMap<>();
        data.put("isFollowing", isFollowing);
        data.put("followerId", followerId);
        data.put("followedId", followedId);

        return ResponseUtil.success(data);
    }

    /**
     * 获取关注列表
     * GET /relation/follow/{userId}/following
     */
    @GetMapping("/follow/{userId}/following")
    public ResponseEntity<Map<String, Object>> getFollowing(@PathVariable Long userId) {
        log.info("获取用户{}的关注列表", userId);
        Set<Long> following = relationService.getFollowing(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("following", following);
        data.put("count", following.size());

        return ResponseUtil.success(data);
    }

    /**
     * 获取粉丝列表
     * GET /relation/follow/{userId}/followers
     */
    @GetMapping("/follow/{userId}/followers")
    public ResponseEntity<Map<String, Object>> getFollowers(@PathVariable Long userId) {
        log.info("获取用户{}的粉丝列表", userId);
        Set<Long> followers = relationService.getFollowers(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("followers", followers);
        data.put("count", followers.size());

        return ResponseUtil.success(data);
    }

    /**
     * 获取关注数和粉丝数
     * GET /relation/follow/{userId}/count
     */
    @GetMapping("/follow/{userId}/count")
    public ResponseEntity<Map<String, Object>> getFollowCount(@PathVariable Long userId) {
        Long followingCount = relationService.getFollowingCount(userId);
        Long followerCount = relationService.getFollowerCount(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("followingCount", followingCount);
        data.put("followerCount", followerCount);

        return ResponseUtil.success(data);
    }

    /**
     * 获取共同关注
     * GET /relation/follow/mutual
     */
    @GetMapping("/follow/mutual")
    public ResponseEntity<Map<String, Object>> getMutualFollowing(
            @RequestParam Long userId1,
            @RequestParam Long userId2) {
        log.info("获取用户{}和用户{}的共同关注", userId1, userId2);
        Set<Long> mutualFollowing = relationService.getMutualFollowing(userId1, userId2);

        Map<String, Object> data = new HashMap<>();
        data.put("userId1", userId1);
        data.put("userId2", userId2);
        data.put("mutualFollowing", mutualFollowing);
        data.put("count", mutualFollowing.size());

        return ResponseUtil.success(data);
    }

    /**
     * 检查是否互相关注
     * GET /relation/follow/mutual/check
     */
    @GetMapping("/follow/mutual/check")
    public ResponseEntity<Map<String, Object>> checkMutualFollowing(
            @RequestParam Long userId1,
            @RequestParam Long userId2) {
        boolean isMutual = relationService.isMutualFollowing(userId1, userId2);

        Map<String, Object> data = new HashMap<>();
        data.put("userId1", userId1);
        data.put("userId2", userId2);
        data.put("isMutualFollowing", isMutual);

        return ResponseUtil.success(data);
    }

    // ==================== 点赞相关 ====================

    /**
     * 点赞帖子
     * POST /relation/like
     */
    @PostMapping("/like")
    public ResponseEntity<Map<String, Object>> likePost(@RequestBody LikeRequest request) {
        log.info("用户{}点赞帖子{}", request.getUserId(), request.getPostId());
        boolean success = relationService.likePost(request.getUserId(), request.getPostId());

        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("userId", request.getUserId());
        data.put("postId", request.getPostId());

        if (success) {
            Long likeCount = relationService.getLikeCount(request.getPostId());
            data.put("likeCount", likeCount);
            return ResponseUtil.success("点赞成功", data);
        } else {
            return ResponseUtil.success("已经点赞过了", data);
        }
    }

    /**
     * 取消点赞
     * DELETE /relation/like
     */
    @DeleteMapping("/like")
    public ResponseEntity<Map<String, Object>> unlikePost(@RequestParam Long userId, @RequestParam Long postId) {
        log.info("用户{}取消点赞帖子{}", userId, postId);
        boolean success = relationService.unlikePost(userId, postId);

        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("userId", userId);
        data.put("postId", postId);

        if (success) {
            Long likeCount = relationService.getLikeCount(postId);
            data.put("likeCount", likeCount);
        }

        return ResponseUtil.success(success ? "取消点赞成功" : "未点赞该帖子", data);
    }

    /**
     * 检查是否已点赞
     * GET /relation/like/check
     */
    @GetMapping("/like/check")
    public ResponseEntity<Map<String, Object>> checkLiked(
            @RequestParam Long userId,
            @RequestParam Long postId) {
        boolean isLiked = relationService.isLiked(userId, postId);

        Map<String, Object> data = new HashMap<>();
        data.put("isLiked", isLiked);
        data.put("userId", userId);
        data.put("postId", postId);

        return ResponseUtil.success(data);
    }

    /**
     * 批量检查点赞状态
     * POST /relation/like/batch-check
     */
    @PostMapping("/like/batch-check")
    public ResponseEntity<Map<String, Object>> batchCheckLiked(@RequestBody BatchCheckRequest request) {
        log.info("批量检查用户{}对{}个帖子的点赞状态", request.getUserId(), request.getPostIds().size());
        Map<Long, Boolean> result = relationService.batchCheckLiked(request.getUserId(), request.getPostIds());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", request.getUserId());
        data.put("likedStatus", result);

        return ResponseUtil.success(data);
    }

    /**
     * 获取点赞用户列表
     * GET /relation/like/{postId}/likers
     */
    @GetMapping("/like/{postId}/likers")
    public ResponseEntity<Map<String, Object>> getLikers(@PathVariable Long postId) {
        log.info("获取帖子{}的点赞用户列表", postId);
        Set<Long> likers = relationService.getLikers(postId);

        Map<String, Object> data = new HashMap<>();
        data.put("postId", postId);
        data.put("likers", likers);
        data.put("count", likers.size());

        return ResponseUtil.success(data);
    }

    /**
     * 获取点赞数
     * GET /relation/like/{postId}/count
     */
    @GetMapping("/like/{postId}/count")
    public ResponseEntity<Map<String, Object>> getLikeCount(@PathVariable Long postId) {
        Long likeCount = relationService.getLikeCount(postId);

        Map<String, Object> data = new HashMap<>();
        data.put("postId", postId);
        data.put("likeCount", likeCount);

        return ResponseUtil.success(data);
    }

    /**
     * 获取用户点赞过的帖子列表
     * GET /relation/like/user/{userId}/posts
     */
    @GetMapping("/like/user/{userId}/posts")
    public ResponseEntity<Map<String, Object>> getUserLikedPosts(@PathVariable Long userId) {
        log.info("获取用户{}点赞过的帖子列表", userId);
        Set<Long> likedPosts = relationService.getUserLikedPosts(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("likedPosts", likedPosts);
        data.put("count", likedPosts.size());

        return ResponseUtil.success(data);
    }

    // ==================== 收藏相关 ====================

    /**
     * 收藏帖子
     * POST /relation/favorite
     */
    @PostMapping("/favorite")
    public ResponseEntity<Map<String, Object>> favoritePost(@RequestBody FavoriteRequest request) {
        log.info("用户{}收藏帖子{}", request.getUserId(), request.getPostId());
        boolean success = relationService.favoritePost(request.getUserId(), request.getPostId());

        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("userId", request.getUserId());
        data.put("postId", request.getPostId());

        if (success) {
            return ResponseUtil.success("收藏成功", data);
        } else {
            return ResponseUtil.success("已经收藏过了", data);
        }
    }

    /**
     * 取消收藏
     * DELETE /relation/favorite
     */
    @DeleteMapping("/favorite")
    public ResponseEntity<Map<String, Object>> unfavoritePost(
            @RequestParam Long userId,
            @RequestParam Long postId) {
        log.info("用户{}取消收藏帖子{}", userId, postId);
        boolean success = relationService.unfavoritePost(userId, postId);

        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("userId", userId);
        data.put("postId", postId);

        return ResponseUtil.success(success ? "取消收藏成功" : "未收藏该帖子", data);
    }

    /**
     * 检查是否已收藏
     * GET /relation/favorite/check
     */
    @GetMapping("/favorite/check")
    public ResponseEntity<Map<String, Object>> checkFavorited(
            @RequestParam Long userId,
            @RequestParam Long postId) {
        boolean isFavorited = relationService.isFavorited(userId, postId);

        Map<String, Object> data = new HashMap<>();
        data.put("isFavorited", isFavorited);
        data.put("userId", userId);
        data.put("postId", postId);

        return ResponseUtil.success(data);
    }

    /**
     * 获取用户收藏列表
     * GET /relation/favorite/user/{userId}
     */
    @GetMapping("/favorite/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserFavorites(@PathVariable Long userId) {
        log.info("获取用户{}的收藏列表", userId);
        Set<Long> favorites = relationService.getUserFavorites(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("favorites", favorites);
        data.put("count", favorites.size());

        return ResponseUtil.success(data);
    }

    /**
     * 获取收藏数
     * GET /relation/favorite/{postId}/count
     */
    @GetMapping("/favorite/{postId}/count")
    public ResponseEntity<Map<String, Object>> getFavoriteCount(@PathVariable Long postId) {
        Long favoriteCount = relationService.getFavoriteCount(postId);

        Map<String, Object> data = new HashMap<>();
        data.put("postId", postId);
        data.put("favoriteCount", favoriteCount);

        return ResponseUtil.success(data);
    }

    // ==================== 黑名单相关 ====================

    /**
     * 拉黑用户
     * POST /relation/block
     */
    @PostMapping("/block")
    public ResponseEntity<Map<String, Object>> blockUser(@RequestBody BlockRequest request) {
        log.info("用户{}拉黑用户{}", request.getUserId(), request.getBlockedUserId());
        boolean success = relationService.blockUser(request.getUserId(), request.getBlockedUserId());

        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("userId", request.getUserId());
        data.put("blockedUserId", request.getBlockedUserId());

        return ResponseUtil.success(success ? "拉黑成功" : "已经拉黑了", data);
    }

    /**
     * 取消拉黑
     * DELETE /relation/block
     */
    @DeleteMapping("/block")
    public ResponseEntity<Map<String, Object>> unblockUser(
            @RequestParam Long userId,
            @RequestParam Long blockedUserId) {
        log.info("用户{}取消拉黑用户{}", userId, blockedUserId);
        boolean success = relationService.unblockUser(userId, blockedUserId);

        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("userId", userId);
        data.put("blockedUserId", blockedUserId);

        return ResponseUtil.success(success ? "取消拉黑成功" : "未拉黑该用户", data);
    }

    /**
     * 检查是否在黑名单中
     * GET /relation/block/check
     */
    @GetMapping("/block/check")
    public ResponseEntity<Map<String, Object>> checkBlocked(
            @RequestParam Long userId,
            @RequestParam Long blockedUserId) {
        boolean isBlocked = relationService.isBlocked(userId, blockedUserId);

        Map<String, Object> data = new HashMap<>();
        data.put("isBlocked", isBlocked);
        data.put("userId", userId);
        data.put("blockedUserId", blockedUserId);

        return ResponseUtil.success(data);
    }

    /**
     * 获取黑名单列表
     * GET /relation/block/user/{userId}
     */
    @GetMapping("/block/user/{userId}")
    public ResponseEntity<Map<String, Object>> getBlacklist(@PathVariable Long userId) {
        log.info("获取用户{}的黑名单列表", userId);
        Set<Long> blacklist = relationService.getBlacklist(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("blacklist", blacklist);
        data.put("count", blacklist.size());

        return ResponseUtil.success(data);
    }

    /**
     * 过滤黑名单用户
     * POST /relation/block/filter
     */
    @PostMapping("/block/filter")
    public ResponseEntity<Map<String, Object>> filterBlacklisted(@RequestBody FilterRequest request) {
        log.info("过滤用户{}的黑名单，原列表大小：{}", request.getUserId(), request.getUserIds().size());
        List<Long> filtered = relationService.filterBlacklisted(request.getUserId(), request.getUserIds());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", request.getUserId());
        data.put("originalCount", request.getUserIds().size());
        data.put("filteredCount", filtered.size());
        data.put("filteredUserIds", filtered);

        return ResponseUtil.success(data);
    }
}
