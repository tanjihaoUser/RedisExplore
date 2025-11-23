# 数据库批量同步（Batch Sync）

## 使用情况

**常用程度：⭐⭐⭐（较常用）**

批量同步是数据迁移、数据恢复、数据一致性保证中常用的批量处理方式。

## 概述

批量同步是指将数据从一个数据源批量同步到另一个数据源，通常用于数据迁移、数据恢复、缓存与数据库同步等场景。相比逐条同步，批量同步可以提升 10-100 倍的性能。

## 生产场景示例

### 1. 缓存到数据库批量同步（Cache to Database Sync）

**场景描述：**
- Redis 缓存中的数据需要同步到数据库
- 数据恢复、数据迁移
- 保证缓存和数据库的一致性

**实现示例：**
```java
// 批量同步点赞关系到数据库
@Transactional
public void batchSyncLikes(Long postId) {
    try {
        // 1. 从 Redis 读取点赞用户列表
        Set<Long> likers = redisTemplate.opsForSet()
            .members("post:like:" + postId);
        
        if (likers == null || likers.isEmpty()) {
            log.info("No likes to sync for post {}", postId);
            return;
        }
        
        // 2. 从数据库读取已存在的点赞关系
        List<PostLike> existing = postLikeMapper.selectByPostId(postId);
        Set<Long> existingUserIds = existing.stream()
            .map(PostLike::getUserId)
            .collect(Collectors.toSet());
        
        // 3. 找出需要新增的点赞关系
        List<PostLike> toInsert = likers.stream()
            .filter(userId -> !existingUserIds.contains(userId))
            .map(userId -> PostLike.builder()
                .postId(postId)
                .userId(userId)
                .build())
            .collect(Collectors.toList());
        
        // 4. 批量插入
        if (!toInsert.isEmpty()) {
            postLikeMapper.batchInsert(toInsert);
            log.info("Batch synced {} likes for post {}", toInsert.size(), postId);
        }
    } catch (Exception e) {
        log.error("Failed to batch sync likes for post {}", postId, e);
        throw new RuntimeException("批量同步点赞关系失败", e);
    }
}
```

**业界案例：**
- **微博**：Redis 点赞数据批量同步到数据库
- **抖音**：Redis 关注关系批量同步到数据库
- **B站**：Redis 收藏数据批量同步到数据库

### 2. 数据库到数据库批量同步（Database to Database Sync）

**场景描述：**
- 数据迁移、数据备份
- 主从数据库同步
- 跨数据库数据同步

**实现示例：**
```java
// 批量同步用户数据
@Transactional
public void batchSyncUsers(Long lastSyncId, int batchSize) {
    try {
        // 1. 从源数据库读取数据
        List<User> users = sourceUserMapper.selectBatch(lastSyncId, batchSize);
        
        if (users.isEmpty()) {
            return;
        }
        
        // 2. 批量插入到目标数据库
        targetUserMapper.batchInsert(users);
        
        // 3. 更新同步进度
        Long maxId = users.stream()
            .map(User::getId)
            .max(Long::compareTo)
            .orElse(lastSyncId);
        
        syncProgressService.updateProgress("user_sync", maxId);
        
        log.info("Synced {} users, last id: {}", users.size(), maxId);
    } catch (Exception e) {
        log.error("Failed to batch sync users", e);
        throw new RuntimeException("批量同步用户数据失败", e);
    }
}
```

**业界案例：**
- **电商系统**：订单数据从生产库同步到分析库
- **金融系统**：交易数据从主库同步到备份库
- **内容平台**：内容数据从主库同步到搜索库

### 3. 数据恢复批量同步（Data Recovery Sync）

**场景描述：**
- 数据丢失后从备份恢复
- 数据修复、数据补偿
- 保证数据完整性

**实现示例：**
```java
// 批量恢复用户关注关系
@Transactional
public void batchRecoverFollows(Long userId) {
    try {
        // 1. 从 Redis 读取关注列表
        Set<Long> following = redisTemplate.opsForSet()
            .members("user:follow:" + userId);
        
        if (following == null || following.isEmpty()) {
            log.info("No follows to recover for user {}", userId);
            return;
        }
        
        // 2. 从数据库读取已存在的关注关系
        List<Long> dbFollowedIds = followMapper.selectFollowedIds(userId);
        Set<Long> dbFollowedSet = new HashSet<>(dbFollowedIds);
        
        // 3. 找出需要恢复的关注关系
        List<UserFollow> toInsert = following.stream()
            .filter(followedId -> !dbFollowedSet.contains(followedId))
            .map(followedId -> UserFollow.builder()
                .followerId(userId)
                .followedId(followedId)
                .build())
            .collect(Collectors.toList());
        
        // 4. 批量插入
        if (!toInsert.isEmpty()) {
            followMapper.batchInsert(toInsert);
            log.info("Recovered {} follows for user {}", toInsert.size(), userId);
        }
    } catch (Exception e) {
        log.error("Failed to recover follows for user {}", userId, e);
        throw new RuntimeException("批量恢复关注关系失败", e);
    }
}
```

**业界案例：**
- **社交平台**：用户关系数据恢复
- **内容平台**：内容数据恢复
- **电商平台**：订单数据恢复

### 4. 增量同步（Incremental Sync）

**场景描述：**
- 只同步变更的数据
- 提高同步效率
- 减少数据库压力

**实现示例：**
```java
// 增量同步用户数据
@Transactional
public void incrementalSyncUsers(Long lastSyncTime) {
    try {
        // 1. 查询上次同步后更新的用户
        List<User> updatedUsers = userMapper.selectUpdatedAfter(lastSyncTime);
        
        if (updatedUsers.isEmpty()) {
            return;
        }
        
        // 2. 批量更新到目标数据库
        for (User user : updatedUsers) {
            if (targetUserMapper.exists(user.getId())) {
                targetUserMapper.update(user);
            } else {
                targetUserMapper.insert(user);
            }
        }
        
        // 3. 更新同步时间
        syncTimeService.updateSyncTime("user_sync", System.currentTimeMillis());
        
        log.info("Incremental synced {} users", updatedUsers.size());
    } catch (Exception e) {
        log.error("Failed to incremental sync users", e);
        throw new RuntimeException("增量同步用户数据失败", e);
    }
}
```

**业界案例：**
- **数据仓库**：增量同步业务数据到数据仓库
- **搜索引擎**：增量同步内容数据到搜索引擎
- **CDN**：增量同步静态资源到 CDN

### 5. 全量同步（Full Sync）

**场景描述：**
- 首次同步、数据重建
- 保证数据完整性
- 数据迁移

**实现示例：**
```java
// 全量同步用户数据
@Transactional
public void fullSyncUsers() {
    try {
        Long lastId = 0L;
        int batchSize = 1000;
        int totalSynced = 0;
        
        while (true) {
            // 1. 分批读取源数据
            List<User> users = sourceUserMapper.selectBatch(lastId, batchSize);
            
            if (users.isEmpty()) {
                break;
            }
            
            // 2. 批量插入到目标数据库
            targetUserMapper.batchInsert(users);
            
            // 3. 更新进度
            lastId = users.stream()
                .map(User::getId)
                .max(Long::compareTo)
                .orElse(lastId);
            
            totalSynced += users.size();
            log.info("Synced {} users, total: {}", users.size(), totalSynced);
            
            // 4. 短暂休眠，避免对数据库造成过大压力
            Thread.sleep(100);
        }
        
        log.info("Full sync completed, total: {} users", totalSynced);
    } catch (Exception e) {
        log.error("Failed to full sync users", e);
        throw new RuntimeException("全量同步用户数据失败", e);
    }
}
```

**业界案例：**
- **数据迁移**：系统迁移时全量同步数据
- **数据重建**：数据损坏后全量重建
- **数据备份**：定期全量备份数据

## MyBatis 实现

### XML 配置方式

```xml
<!-- 批量同步：批量插入 -->
<insert id="batchInsert">
    INSERT INTO post_like (post_id, user_id, created_at) VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.postId}, #{item.userId}, #{item.createdAt})
    </foreach>
</insert>

<!-- 批量同步：批量查询已存在的数据 -->
<select id="selectByPostId" resultType="com.wait.entity.domain.PostLike">
    SELECT post_id AS postId, user_id AS userId
    FROM post_like
    WHERE post_id = #{postId}
</select>

<!-- 增量同步：查询更新的数据 -->
<select id="selectUpdatedAfter" resultType="com.wait.entity.domain.User">
    SELECT id, username, email, update_time
    FROM user_base
    WHERE update_time > #{lastSyncTime}
    ORDER BY id
    LIMIT #{limit}
</select>
```

### 分批同步实现

```java
// 分批同步，避免内存溢出
public void batchSyncWithPagination(Long postId) {
    int pageSize = 1000;
    int offset = 0;
    
    while (true) {
        // 1. 分批从 Redis 读取
        Set<Long> likers = redisTemplate.opsForSet()
            .members("post:like:" + postId);
        
        if (likers == null || likers.isEmpty()) {
            break;
        }
        
        // 2. 转换为列表并分页
        List<Long> likerList = new ArrayList<>(likers);
        int end = Math.min(offset + pageSize, likerList.size());
        List<Long> page = likerList.subList(offset, end);
        
        if (page.isEmpty()) {
            break;
        }
        
        // 3. 批量同步当前页
        syncLikesPage(postId, page);
        
        offset += pageSize;
        
        // 4. 短暂休眠
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
}
```

## 优点

1. **性能优秀**
   - 批量操作，减少数据库交互次数
   - 性能提升 10-100 倍

2. **数据一致性**
   - 可以保证数据的一致性
   - 支持事务管理

3. **可恢复性**
   - 支持断点续传
   - 支持失败重试

4. **灵活性**
   - 支持全量同步和增量同步
   - 支持自定义同步策略

## 缺点

1. **数据延迟**
   - 批量同步可能存在数据延迟
   - 不适合实时性要求高的场景

2. **资源消耗**
   - 大批量同步可能消耗大量资源
   - 可能影响其他业务

3. **数据冲突**
   - 同步过程中可能出现数据冲突
   - 需要处理冲突策略

4. **实现复杂**
   - 需要处理各种异常情况
   - 需要监控和告警机制

## 可能存在的问题及解决方案

### 问题 1：数据冲突

**问题描述：**
- 同步过程中，源数据和目标数据可能不一致
- 可能出现数据冲突

**解决方案：**
1. **覆盖策略**
```java
// 使用 INSERT ... ON DUPLICATE KEY UPDATE
public void batchSyncWithOverwrite(List<User> users) {
    userMapper.batchInsertOrUpdate(users);
}
```

2. **跳过策略**
```java
// 跳过已存在的数据
public void batchSyncWithSkip(List<User> users) {
    List<User> existing = userMapper.selectByIds(
        users.stream().map(User::getId).collect(Collectors.toList())
    );
    Set<Long> existingIds = existing.stream()
        .map(User::getId)
        .collect(Collectors.toSet());
    
    List<User> toInsert = users.stream()
        .filter(user -> !existingIds.contains(user.getId()))
        .collect(Collectors.toList());
    
    if (!toInsert.isEmpty()) {
        userMapper.batchInsert(toInsert);
    }
}
```

3. **时间戳策略**
```java
// 只同步更新的数据
public void batchSyncWithTimestamp(List<User> users, Long lastSyncTime) {
    List<User> toSync = users.stream()
        .filter(user -> user.getUpdateTime() > lastSyncTime)
        .collect(Collectors.toList());
    
    if (!toSync.isEmpty()) {
        userMapper.batchInsertOrUpdate(toSync);
    }
}
```

### 问题 2：内存溢出

**问题描述：**
- 大批量同步时，可能一次性加载大量数据到内存
- 可能导致内存溢出

**解决方案：**
1. **分批同步**
```java
// 分批同步，控制内存使用
public void batchSyncWithBatching(Long postId) {
    int batchSize = 1000;
    int offset = 0;
    
    while (true) {
        List<PostLike> batch = loadBatch(postId, offset, batchSize);
        if (batch.isEmpty()) {
            break;
        }
        
        syncBatch(batch);
        offset += batchSize;
    }
}
```

2. **流式处理**
```java
// 使用流式处理，不一次性加载所有数据
public void batchSyncWithStream(Long postId) {
    try (Stream<Long> likerStream = loadLikersStream(postId)) {
        List<PostLike> batch = new ArrayList<>();
        int batchSize = 1000;
        
        likerStream.forEach(userId -> {
            batch.add(PostLike.builder()
                .postId(postId)
                .userId(userId)
                .build());
            
            if (batch.size() >= batchSize) {
                syncBatch(new ArrayList<>(batch));
                batch.clear();
            }
        });
        
        // 处理剩余数据
        if (!batch.isEmpty()) {
            syncBatch(batch);
        }
    }
}
```

### 问题 3：同步失败

**问题描述：**
- 同步过程中可能失败
- 需要支持断点续传和重试

**解决方案：**
1. **断点续传**
```java
// 记录同步进度，支持断点续传
public void batchSyncWithCheckpoint(Long postId) {
    Long lastSyncedId = syncProgressService.getLastSyncedId("like_sync", postId);
    int batchSize = 1000;
    
    while (true) {
        List<PostLike> batch = loadBatchAfter(postId, lastSyncedId, batchSize);
        if (batch.isEmpty()) {
            break;
        }
        
        try {
            syncBatch(batch);
            lastSyncedId = batch.stream()
                .map(PostLike::getId)
                .max(Long::compareTo)
                .orElse(lastSyncedId);
            syncProgressService.updateProgress("like_sync", postId, lastSyncedId);
        } catch (Exception e) {
            log.error("Sync failed at id: {}", lastSyncedId, e);
            throw e; // 下次从断点继续
        }
    }
}
```

2. **重试机制**
```java
// 使用重试机制
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
public void batchSyncWithRetry(List<PostLike> batch) {
    postLikeMapper.batchInsert(batch);
}
```

### 问题 4：性能影响

**问题描述：**
- 大批量同步可能影响数据库性能
- 可能影响其他业务

**解决方案：**
1. **在非高峰期执行**
```java
// 在低峰期执行批量同步
@Scheduled(cron = "0 0 2 * * ?") // 凌晨2点执行
public void batchSyncInLowTraffic() {
    batchSyncAll();
}
```

2. **限流控制**
```java
// 限流控制，避免对数据库造成过大压力
public void batchSyncWithRateLimit(Long postId) {
    RateLimiter rateLimiter = RateLimiter.create(100); // 每秒100次
    
    int batchSize = 1000;
    int offset = 0;
    
    while (true) {
        rateLimiter.acquire(); // 限流
        
        List<PostLike> batch = loadBatch(postId, offset, batchSize);
        if (batch.isEmpty()) {
            break;
        }
        
        syncBatch(batch);
        offset += batchSize;
    }
}
```

3. **分批提交**
```java
// 分批提交事务，减少锁持有时间
public void batchSyncWithBatching(Long postId) {
    int batchSize = 500;
    int offset = 0;
    
    while (true) {
        List<PostLike> batch = loadBatch(postId, offset, batchSize);
        if (batch.isEmpty()) {
            break;
        }
        
        // 每批单独事务
        transactionTemplate.execute(status -> {
            syncBatch(batch);
            return null;
        });
        
        offset += batchSize;
    }
}
```

## 最佳实践

1. **同步策略选择**
   - 全量同步：首次同步、数据重建
   - 增量同步：日常同步、提高效率

2. **批量大小控制**
   - 小批量（< 1000 条）：直接同步
   - 中批量（1000-10000 条）：推荐批量同步
   - 大批量（> 10000 条）：分批同步，每批 1000-5000 条

3. **错误处理**
   - 支持断点续传
   - 支持失败重试
   - 记录同步日志

4. **性能优化**
   - 在非高峰期执行
   - 使用限流控制
   - 分批提交事务

5. **监控告警**
   - 监控同步进度
   - 监控同步耗时
   - 监控失败率和重试次数

## 性能对比

| 同步方式 | 1000条记录耗时 | 10000条记录耗时 | 100000条记录耗时 |
|---------|--------------|---------------|----------------|
| 逐条同步 | 5000-10000ms | 50000-100000ms | 500000-1000000ms |
| 批量同步 | 50-200ms | 500-2000ms | 5000-20000ms |
| **性能提升** | **25-200倍** | **25-200倍** | **25-200倍** |

## 总结

批量同步是数据迁移、数据恢复、数据一致性保证中常用的批量处理方式，适用于：
- ✅ 缓存到数据库同步
- ✅ 数据库到数据库同步
- ✅ 数据恢复同步
- ✅ 增量同步
- ✅ 全量同步
- ✅ 任何需要批量同步数据的场景

**关键要点：**
1. 选择合适的同步策略（全量/增量）
2. 控制批量大小（1000-5000 条/批）
3. 支持断点续传和失败重试
4. 在非高峰期执行大批量同步
5. 监控同步进度和性能指标

