package com.wait.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wait.entity.domain.PostFavorite;
import com.wait.entity.domain.PostLike;
import com.wait.entity.domain.UserFollow;
import com.wait.mapper.FollowMapper;
import com.wait.mapper.PostFavoriteMapper;
import com.wait.mapper.PostLikeMapper;
import com.wait.service.RelationPersistenceService;
import com.wait.util.AsyncSQLWrapper;
import com.wait.util.BoundUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 * 关系数据持久化服务实现
 * 
 * 业界常见做法：
 * 
 * 1. **关注关系（Follow）**：采用 Write-Through（写透）策略
 * - 原因：关注关系是核心用户关系数据，需要持久化且重要性高
 * - 策略：立即写入数据库，保证数据强一致性
 * - 适用场景：用户关注、取消关注操作
 * 
 * 2. **点赞关系（Like）**：采用 Write-Behind（写回）策略 - 定时+定量批量写入
 * - 原因：点赞操作高频，对实时性要求高，但可接受短暂的数据不一致
 * - 策略：先写 Redis，缓冲操作，定时（如 5 分钟）或定量（如 100 条）批量写入数据库
 * - 优势：减少数据库压力，提高响应速度，批量写入效率更高
 * - 适用场景：帖子点赞、评论点赞等高频操作
 * - 实现：参考 IncrementalWriteStrategy，使用 ConcurrentHashMap 缓冲 + ThreadPoolTaskScheduler 定时执行
 * 
 * 3. **收藏关系（Favorite）**：采用 Write-Behind（写回）策略 - 定时+定量批量写入
 * - 原因：类似点赞，高频操作，但可接受最终一致性
 * - 策略：先写 Redis，缓冲操作，定时或定量批量写入数据库
 * - 适用场景：帖子收藏、文章收藏等
 * 
 * 4. **黑名单（Block）**：采用 Write-Through（写透）策略
 * - 原因：黑名单关系重要，需要立即生效，保证数据一致性
 * - 策略：立即写入数据库
 * - 适用场景：用户拉黑、取消拉黑操作
 * 
 * 批量写入策略（业界常见方案）：
 * - **定时批量写入**：每 N 秒/分钟批量写入一次（如 5 分钟），适合低峰期平滑写入
 * - **定量批量写入**：当缓冲队列达到 M 条时立即写入（如 100 条），适合高峰期快速响应
 * - **混合策略**：定时 + 定量双重触发，兼顾性能和实时性
 * - **去重合并**：同一 key 的多次操作只保留最新状态（如点赞后取消点赞，最终状态为未点赞）
 * 
 * 性能优化：
 * - 批量操作：将多个操作合并为批量写入，减少数据库交互次数
 * - 异步写入：使用线程池异步执行数据库写入，不阻塞主流程
 * - 事务控制：使用 @Transactional 保证数据一致性
 * - 去重优化：相同操作的多次更新只保留最新状态，减少无效写入
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationPersistenceServiceImpl implements RelationPersistenceService {

    private final BoundUtil boundUtil;

    private final FollowMapper followMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final AsyncSQLWrapper asyncSQLWrapper;

    @Qualifier("refreshScheduler")
    private final ThreadPoolTaskScheduler taskScheduler;

    // Redis Key 前缀（与 RelationServiceImpl 保持一致）
    private static final String USER_FOLLOW_PREFIX = "user:follow:";
    private static final String POST_LIKE_PREFIX = "post:like:";
    private static final String USER_FAVORITE_PREFIX = "user:favorite:";

    // ==================== 批量写入配置 ====================
    /** 定时批量写入延迟时间：30 s */
    private static final long BATCH_FLUSH_DELAY_MS = TimeUnit.SECONDS.toMillis(30);

    /** 定量批量写入阈值：当缓冲达到 5 条时立即写入 */
    private static final int BATCH_SIZE_THRESHOLD = 5;

    // ==================== 批量缓冲 ====================
    /** 批量任务缓冲：使用单例任务对象，所有操作都缓冲到同一个任务中 */
    private final RelationBatchTask batchTask = new RelationBatchTask();

    /** 定时刷库任务：用于定时批量写入 */
    private volatile ScheduledFuture<?> scheduledFlushTask;

    // ==================== 关注关系持久化（Write-Through）====================

    @Override
    @Transactional
    public void persistFollow(Long followerId, Long followedId, boolean isFollow) {
        // Write-Through 策略：同步执行，使用 AsyncSQLWrapper 统一管理重试
        asyncSQLWrapper.executeSync(() -> {
            if (isFollow) {
                // 检查数据库中是否已存在
                boolean exists = followMapper.exists(followerId, followedId);
                if (!exists) {
                    UserFollow userFollow = UserFollow.builder()
                            .followerId(followerId)
                            .followedId(followedId)
                            .build();
                    followMapper.insert(userFollow);
                    log.info("Persisted follow: user {} follows user {}", followerId, followedId);
                } else {
                    log.debug("Follow already exists in DB: user {} follows user {}", followerId, followedId);
                }
            } else {
                // 取消关注
                int deleted = followMapper.delete(followerId, followedId);
                if (deleted > 0) {
                    log.info("Persisted unfollow: user {} unfollows user {}", followerId, followedId);
                } else {
                    log.debug("Follow not found in DB: user {} unfollows user {}", followerId, followedId);
                }
            }
            return null;
        });
    }

    // ==================== 点赞关系持久化（Write-Behind - 定时+定量批量写入）====================

    @Override
    public CompletableFuture<Void> persistLike(Long userId, Long postId, boolean isLike) {
        // 1. 将操作添加到缓冲队列（去重：同一 key 的多次操作只保留最新状态）
        batchTask.addLikeOperation(postId, userId, isLike);

        // 2. 检查是否达到定量阈值，如果达到则立即触发批量写入
        if (batchTask.getTotalOperationCount() >= BATCH_SIZE_THRESHOLD) {
            log.debug("Batch size threshold reached ({}), triggering immediate flush",
                    batchTask.getTotalOperationCount());
            flushBatchToDatabase();
                } else {
            // 3. 未达到阈值，确保定时任务已启动（如果未启动）
            scheduleBatchFlushTask();
                }

        // 4. 立即返回 CompletableFuture，不阻塞主流程
        return CompletableFuture.completedFuture(null);
    }

    // ==================== 收藏关系持久化（Write-Behind - 定时+定量批量写入）====================

    @Override
    public CompletableFuture<Void> persistFavorite(Long userId, Long postId, boolean isFavorite) {
        // 1. 将操作添加到缓冲队列（去重：同一 key 的多次操作只保留最新状态）
        batchTask.addFavoriteOperation(userId, postId, isFavorite);

        // 2. 检查是否达到定量阈值，如果达到则立即触发批量写入
        if (batchTask.getTotalOperationCount() >= BATCH_SIZE_THRESHOLD) {
            log.debug("Batch size threshold reached ({}), triggering immediate flush",
                    batchTask.getTotalOperationCount());
            flushBatchToDatabase();
                } else {
            // 3. 未达到阈值，确保定时任务已启动（如果未启动）
            scheduleBatchFlushTask();
                }

        // 4. 立即返回 CompletableFuture，不阻塞主流程
        return CompletableFuture.completedFuture(null);
    }

    // ==================== 黑名单持久化（Write-Through）====================

    @Override
    @Transactional
    public void persistBlock(Long userId, Long blockedUserId, boolean isBlock) {
        // Write-Through 策略：同步执行，使用 AsyncSQLWrapper 统一管理重试
        asyncSQLWrapper.executeSync(() -> {
            // 黑名单通常有单独的表，这里简化为使用现有的 FollowMapper
            // 实际项目中应该创建 BlockMapper
            // 为了示例，这里使用注释说明

            if (isBlock) {
                // TODO: 实际项目中创建 BlockMapper
                // Block block = Block.builder()
                // .userId(userId)
                // .blockedUserId(blockedUserId)
                // .build();
                // blockMapper.insert(block);
                log.info("Persisted block: user {} blocks user {}", userId, blockedUserId);
            } else {
                // TODO: blockMapper.delete(userId, blockedUserId);
                log.info("Persisted unblock: user {} unblocks user {}", userId, blockedUserId);
            }
            return null;
        });
    }

    // ==================== 批量同步（用于数据恢复/迁移）====================

    @Override
    @Transactional
    public void batchSyncFollows(Long userId) {
        try {
            // 从 Redis 读取关注列表
            Set<Long> following = boundUtil.sMembers(USER_FOLLOW_PREFIX + userId, Long.class);
            if (following == null || following.isEmpty()) {
                log.info("No follows to sync for user {}", userId);
                return;
            }

            // 从数据库读取已存在的关注关系
            List<Long> dbFollowedIds = followMapper.selectFollowedIds(userId);
            Set<Long> dbFollowedSet = new HashSet<>(dbFollowedIds);

            // 找出需要新增的关注关系（在 Redis 中但不在数据库中）
            List<UserFollow> toInsert = new ArrayList<>();
            for (Long followedId : following) {
                if (!dbFollowedSet.contains(followedId)) {
                    toInsert.add(UserFollow.builder()
                            .followerId(userId)
                            .followedId(followedId)
                            .build());
                }
            }

            // 批量插入
            if (!toInsert.isEmpty()) {
                for (UserFollow follow : toInsert) {
                    followMapper.insert(follow);
                }
                log.info("Batch synced {} follows for user {}", toInsert.size(), userId);
            } else {
                log.debug("All follows already synced for user {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to batch sync follows for user {}", userId, e);
            throw new RuntimeException("批量同步关注关系失败", e);
        }
    }

    @Override
    @Transactional
    public void batchSyncLikes(Long postId) {
        try {
            // 从 Redis 读取点赞用户列表
            Set<Long> likers = boundUtil.sMembers(POST_LIKE_PREFIX + postId, Long.class);
            if (likers == null || likers.isEmpty()) {
                log.info("No likes to sync for post {}", postId);
                return;
            }

            // 批量插入（简化版，实际项目中应该检查是否存在）
            List<PostLike> toInsert = new ArrayList<>();
            for (Long userId : likers) {
                boolean exists = postLikeMapper.exists(postId, userId);
                if (!exists) {
                    toInsert.add(PostLike.builder()
                            .postId(postId)
                            .userId(userId)
                            .build());
                }
            }

            // 批量插入
            if (!toInsert.isEmpty()) {
                if (toInsert.size() == 1) {
                    postLikeMapper.insert(toInsert.get(0));
                } else {
                    postLikeMapper.batchInsert(toInsert);
                }
                log.info("Batch synced {} likes for post {}", toInsert.size(), postId);
            } else {
                log.debug("All likes already synced for post {}", postId);
            }
        } catch (Exception e) {
            log.error("Failed to batch sync likes for post {}", postId, e);
            throw new RuntimeException("批量同步点赞关系失败", e);
        }
    }

    @Override
    @Transactional
    public void batchSyncFavorites(Long userId) {
        try {
            // 从 Redis 读取收藏列表
            Set<Long> favorites = boundUtil.sMembers(USER_FAVORITE_PREFIX + userId, Long.class);
            if (favorites == null || favorites.isEmpty()) {
                log.info("No favorites to sync for user {}", userId);
                return;
            }

            // 批量插入（简化版）
            List<PostFavorite> toInsert = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Long postId : favorites) {
                boolean exists = postFavoriteMapper.exists(userId, postId);
                if (!exists) {
                    toInsert.add(PostFavorite.builder()
                            .userId(userId)
                            .postId(postId)
                            .createdAt(now)
                            .build());
                }
            }

            // 批量插入
            if (!toInsert.isEmpty()) {
                // 可以使用批量插入优化（如果 Mapper 支持）
                if (toInsert.size() == 1) {
                    postFavoriteMapper.insert(toInsert.get(0));
                } else {
                    postFavoriteMapper.batchInsert(toInsert);
                }
                log.info("Batch synced {} favorites for user {}", toInsert.size(), userId);
            } else {
                log.debug("All favorites already synced for user {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to batch sync favorites for user {}", userId, e);
            throw new RuntimeException("批量同步收藏关系失败", e);
        }
    }

    // ==================== 批量写入核心方法 ====================

    /**
     * 启动定时批量刷库任务
     * 只在任务不存在时创建，避免每次操作都重置定时器
     */
    private void scheduleBatchFlushTask() {
        // 如果任务已存在且未完成，不重置定时器，保持固定刷新周期
        if (scheduledFlushTask != null && !scheduledFlushTask.isDone() && !scheduledFlushTask.isCancelled()) {
            return;
        }

        // 任务不存在或已取消/完成，创建新任务
        scheduledFlushTask = taskScheduler.schedule(
                this::flushBatchToDatabase,
                new Date(System.currentTimeMillis() + BATCH_FLUSH_DELAY_MS));

        log.debug("Scheduled batch flush task, delay: {}ms", BATCH_FLUSH_DELAY_MS);
    }

    /**
     * 批量刷写到数据库 - 核心方法
     * 将缓冲的点赞和收藏操作批量写入数据库
     */
    @Transactional
    public void flushBatchToDatabase() {
        // 1. 获取当前缓冲的任务（使用同步块确保线程安全）
        RelationBatchTask currentTask;
        synchronized (batchTask) {
            if (!batchTask.hasPendingOperations()) {
                log.debug("No pending operations to flush");
                return;
            }

            // 创建当前任务的快照，避免在写入过程中新操作影响
            currentTask = new RelationBatchTask();
            currentTask.getLikeOperations().putAll(batchTask.getLikeOperations());
            currentTask.getFavoriteOperations().putAll(batchTask.getFavoriteOperations());

            // 清空原任务，准备接收新操作
            batchTask.clear();
        }

        // 2. 取消已存在的定时任务（因为已经手动触发了）
        if (scheduledFlushTask != null && !scheduledFlushTask.isDone()) {
            scheduledFlushTask.cancel(false);
            scheduledFlushTask = null;
        }

        // 3. 异步执行批量写入，不阻塞主流程
        asyncSQLWrapper.executeAsyncVoid(() -> {
            try {
                flushLikesToDatabase(currentTask);
                flushFavoritesToDatabase(currentTask);
                log.info("Batch flush completed: {} likes, {} favorites",
                        currentTask.getLikeOperationCount(),
                        currentTask.getFavoriteOperationCount());
            } catch (Exception e) {
                log.error("Failed to flush batch to database", e);
                // 写入失败时，将任务重新放回缓冲队列（补偿机制）
                synchronized (batchTask) {
                    batchTask.getLikeOperations().putAll(currentTask.getLikeOperations());
                    batchTask.getFavoriteOperations().putAll(currentTask.getFavoriteOperations());
                }
                throw e;
            }
        });
    }

    /**
     * 批量写入点赞操作到数据库
     */
    private void flushLikesToDatabase(RelationBatchTask task) {
        if (task.getLikeOperations().isEmpty()) {
            return;
        }

        // 分离点赞和取消点赞操作
        List<PostLike> likeCandidates = new ArrayList<>();
        List<PostLike> toDelete = new ArrayList<>();

        for (java.util.Map.Entry<String, Boolean> entry : task.getLikeOperations().entrySet()) {
            String[] parts = entry.getKey().split(":");
            Long postId = Long.parseLong(parts[0]);
            Long userId = Long.parseLong(parts[1]);
            boolean isLike = entry.getValue();

            if (isLike) {
                likeCandidates.add(PostLike.builder()
                        .postId(postId)
                        .userId(userId)
                        .build());
            } else {
                toDelete.add(PostLike.builder()
                        .postId(postId)
                        .userId(userId)
                        .build());
            }
        }

        // 批量查询已存在的点赞关系（优化：减少数据库交互次数）
        Set<String> existingLikes = new HashSet<>();
        if (!likeCandidates.isEmpty()) {
            List<PostLike> existing = postLikeMapper.batchExists(likeCandidates);
            for (PostLike like : existing) {
                existingLikes.add(like.getPostId() + ":" + like.getUserId());
            }
        }

        // 过滤出需要插入的点赞关系（排除已存在的）
        List<PostLike> toInsert = new ArrayList<>();
        for (PostLike candidate : likeCandidates) {
            String key = candidate.getPostId() + ":" + candidate.getUserId();
            if (!existingLikes.contains(key)) {
                toInsert.add(candidate);
            }
        }

        // 批量插入点赞
        if (!toInsert.isEmpty()) {
            if (toInsert.size() == 1) {
                postLikeMapper.insert(toInsert.get(0));
            } else {
                postLikeMapper.batchInsert(toInsert);
            }
            log.debug("Batch inserted {} likes", toInsert.size());
        }

        // 批量删除取消点赞（MyBatis 批量删除需要循环调用，或使用 IN 子句）
        int deletedCount = 0;
        for (PostLike like : toDelete) {
            int deleted = postLikeMapper.delete(like.getPostId(), like.getUserId());
            if (deleted > 0) {
                deletedCount++;
            }
        }
        if (deletedCount > 0) {
            log.debug("Batch deleted {} unlikes", deletedCount);
        }
    }

    /**
     * 批量写入收藏操作到数据库
     */
    private void flushFavoritesToDatabase(RelationBatchTask task) {
        if (task.getFavoriteOperations().isEmpty()) {
            return;
        }

        // 分离收藏和取消收藏操作
        List<PostFavorite> favoriteCandidates = new ArrayList<>();
        List<PostFavorite> toDelete = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (java.util.Map.Entry<String, Boolean> entry : task.getFavoriteOperations().entrySet()) {
            String[] parts = entry.getKey().split(":");
            Long userId = Long.parseLong(parts[0]);
            Long postId = Long.parseLong(parts[1]);
            boolean isFavorite = entry.getValue();

            if (isFavorite) {
                favoriteCandidates.add(PostFavorite.builder()
                        .userId(userId)
                        .postId(postId)
                        .createdAt(now)
                        .build());
            } else {
                toDelete.add(PostFavorite.builder()
                        .userId(userId)
                        .postId(postId)
                        .build());
            }
        }

        // 批量查询已存在的收藏关系（优化：减少数据库交互次数）
        Set<String> existingFavorites = new HashSet<>();
        if (!favoriteCandidates.isEmpty()) {
            List<PostFavorite> existing = postFavoriteMapper.batchExists(favoriteCandidates);
            for (PostFavorite favorite : existing) {
                existingFavorites.add(favorite.getUserId() + ":" + favorite.getPostId());
            }
        }

        // 过滤出需要插入的收藏关系（排除已存在的）
        List<PostFavorite> toInsert = new ArrayList<>();
        for (PostFavorite candidate : favoriteCandidates) {
            String key = candidate.getUserId() + ":" + candidate.getPostId();
            if (!existingFavorites.contains(key)) {
                toInsert.add(candidate);
            }
        }

        // 批量插入收藏
        if (!toInsert.isEmpty()) {
            if (toInsert.size() == 1) {
                postFavoriteMapper.insert(toInsert.get(0));
            } else {
                postFavoriteMapper.batchInsert(toInsert);
            }
            log.debug("Batch inserted {} favorites", toInsert.size());
        }

        // 批量删除取消收藏
        int deletedCount = 0;
        for (PostFavorite favorite : toDelete) {
            int deleted = postFavoriteMapper.delete(favorite.getUserId(), favorite.getPostId());
            if (deleted > 0) {
                deletedCount++;
            }
        }
        if (deletedCount > 0) {
            log.debug("Batch deleted {} unfavorites", deletedCount);
        }
    }
}
