# 数据库批量写入策略（Write-Behind Batch Write）

## 使用情况

**常用程度：⭐⭐⭐⭐（常用）**

Write-Behind（写回）批量写入策略是高性能系统中常用的缓存写入策略，特别适用于高频写入场景。

## 概述

Write-Behind 批量写入策略是指先将数据写入缓存（如 Redis），然后在后台批量异步写入数据库。这种策略可以显著提高写入性能，减少数据库压力，适用于对实时性要求高但可以接受短暂数据不一致的场景。

## 核心特点

1. **先写缓存，后写数据库**：数据先写入缓存，立即返回，后台异步批量写入数据库
2. **批量合并**：将多个操作合并为批量操作，减少数据库交互次数
3. **双重触发**：支持定时触发和定量触发两种方式
4. **去重合并**：同一 key 的多次操作只保留最新状态

## 生产场景示例

### 1. 点赞关系批量写入（Like Relation）

**场景描述：**
- 用户点赞操作高频，每秒可能有数千次
- 需要快速响应，不能阻塞用户操作
- 可以接受短暂的数据不一致（最终一致性）

**实现示例：**
```java
// 点赞关系批量写入
public CompletableFuture<Void> persistLike(Long userId, Long postId, boolean isLike) {
    // 1. 立即写入 Redis
    String key = "post:like:" + postId;
    if (isLike) {
        redisTemplate.opsForSet().add(key, userId);
    } else {
        redisTemplate.opsForSet().remove(key, userId);
    }
    
    // 2. 添加到批量任务缓冲
    batchTask.addLikeOperation(postId, userId, isLike);
    
    // 3. 检查是否达到定量阈值
    if (batchTask.getTotalOperationCount() >= BATCH_SIZE_THRESHOLD) {
        flushBatchToDatabase(); // 立即触发批量写入
    } else {
        scheduleBatchFlushTask(); // 定时触发
    }
    
    // 4. 立即返回，不阻塞
    return CompletableFuture.completedFuture(null);
}
```

**业界案例：**
- **抖音**：视频点赞批量写入，定时+定量双重触发
- **微博**：微博点赞批量写入，每5分钟或100条触发一次
- **B站**：视频投币批量写入，高峰期定量触发，低峰期定时触发

### 2. 收藏关系批量写入（Favorite Relation）

**场景描述：**
- 用户收藏操作高频
- 需要快速响应
- 可以接受最终一致性

**实现示例：**
```java
// 收藏关系批量写入
public CompletableFuture<Void> persistFavorite(Long userId, Long postId, boolean isFavorite) {
    // 1. 立即写入 Redis
    String key = "user:favorite:" + userId;
    if (isFavorite) {
        redisTemplate.opsForSet().add(key, postId);
    } else {
        redisTemplate.opsForSet().remove(key, postId);
    }
    
    // 2. 添加到批量任务缓冲
    batchTask.addFavoriteOperation(userId, postId, isFavorite);
    
    // 3. 检查是否达到定量阈值
    if (batchTask.getTotalOperationCount() >= BATCH_SIZE_THRESHOLD) {
        flushBatchToDatabase();
    } else {
        scheduleBatchFlushTask();
    }
    
    return CompletableFuture.completedFuture(null);
}
```

**业界案例：**
- **小红书**：笔记收藏批量写入
- **知乎**：回答收藏批量写入
- **GitHub**：仓库 Star 批量写入

### 3. 计数器批量写入（Counter Batch Write）

**场景描述：**
- 点赞数、评论数、浏览数等计数器高频更新
- 需要快速响应
- 可以接受短暂的数据不一致

**实现示例：**
```java
// 计数器批量写入
public void incrementLikeCount(Long postId, int delta) {
    // 1. 立即更新 Redis 计数器
    String key = "post:like_count:" + postId;
    redisTemplate.opsForValue().increment(key, delta);
    
    // 2. 添加到批量任务缓冲（增量更新）
    counterBatchTask.addIncrement(postId, "like_count", delta);
    
    // 3. 检查是否达到定量阈值
    if (counterBatchTask.getTotalOperationCount() >= BATCH_SIZE_THRESHOLD) {
        flushCountersToDatabase();
    } else {
        scheduleCounterFlushTask();
    }
}
```

**业界案例：**
- **微博**：转发数、评论数、点赞数批量写入
- **B站**：播放数、弹幕数、投币数批量写入
- **GitHub**：Star 数、Fork 数批量写入

### 4. 用户行为日志批量写入（User Behavior Log）

**场景描述：**
- 用户行为日志高频产生
- 需要快速记录，不能阻塞主业务流程
- 可以接受短暂的数据丢失（最终一致性）

**实现示例：**
```java
// 用户行为日志批量写入
public void logUserBehavior(UserBehavior behavior) {
    // 1. 立即写入 Redis（可选，用于实时分析）
    String key = "user:behavior:" + behavior.getUserId();
    redisTemplate.opsForList().leftPush(key, behavior);
    
    // 2. 添加到批量任务缓冲
    logBatchTask.addBehavior(behavior);
    
    // 3. 检查是否达到定量阈值
    if (logBatchTask.getTotalOperationCount() >= BATCH_SIZE_THRESHOLD) {
        flushLogsToDatabase();
    } else {
        scheduleLogFlushTask();
    }
}
```

**业界案例：**
- **淘宝**：用户浏览行为日志批量写入
- **抖音**：视频播放行为日志批量写入
- **微信**：消息发送日志批量写入

## 实现方式

### 1. 定时触发（Scheduled Trigger）

**特点：**
- 固定时间间隔批量写入
- 适合低峰期平滑写入
- 可以控制写入频率

**实现示例：**
```java
// 定时批量写入
private void scheduleBatchFlushTask() {
    if (scheduledFlushTask != null && !scheduledFlushTask.isDone()) {
        return; // 任务已存在，不重复创建
    }
    
    scheduledFlushTask = taskScheduler.schedule(
        this::flushBatchToDatabase,
        new Date(System.currentTimeMillis() + BATCH_FLUSH_DELAY_MS)
    );
}

// 配置：每30秒批量写入一次
private static final long BATCH_FLUSH_DELAY_MS = TimeUnit.SECONDS.toMillis(30);
```

### 2. 定量触发（Size-based Trigger）

**特点：**
- 达到指定数量立即批量写入
- 适合高峰期快速响应
- 可以控制批量大小

**实现示例：**
```java
// 定量批量写入
public void persistLike(Long userId, Long postId, boolean isLike) {
    // 1. 写入 Redis
    updateRedisLike(postId, userId, isLike);
    
    // 2. 添加到缓冲
    batchTask.addLikeOperation(postId, userId, isLike);
    
    // 3. 检查是否达到阈值
    if (batchTask.getTotalOperationCount() >= BATCH_SIZE_THRESHOLD) {
        flushBatchToDatabase(); // 立即触发
    } else {
        scheduleBatchFlushTask(); // 定时触发
    }
}

// 配置：达到100条立即写入
private static final int BATCH_SIZE_THRESHOLD = 100;
```

### 3. 混合策略（Hybrid Strategy）

**特点：**
- 同时支持定时和定量触发
- 兼顾性能和实时性
- 推荐使用

**实现示例：**
```java
// 混合策略：定时 + 定量
public void persistLike(Long userId, Long postId, boolean isLike) {
    // 1. 写入 Redis
    updateRedisLike(postId, userId, isLike);
    
    // 2. 添加到缓冲
    batchTask.addLikeOperation(postId, userId, isLike);
    
    // 3. 定量触发：达到阈值立即写入
    if (batchTask.getTotalOperationCount() >= BATCH_SIZE_THRESHOLD) {
        flushBatchToDatabase();
    } else {
        // 4. 定时触发：确保定期写入
        scheduleBatchFlushTask();
    }
}
```

## 批量写入核心实现

### 1. 批量任务缓冲（Batch Task Buffer）

```java
public class RelationBatchTask {
    // 点赞操作缓冲：key = "postId:userId", value = true(点赞) / false(取消点赞)
    private ConcurrentMap<String, Boolean> likeOperations = new ConcurrentHashMap<>();
    
    // 收藏操作缓冲：key = "userId:postId", value = true(收藏) / false(取消收藏)
    private ConcurrentMap<String, Boolean> favoriteOperations = new ConcurrentHashMap<>();
    
    // 添加点赞操作（去重：同一 key 的多次操作只保留最新状态）
    public void addLikeOperation(Long postId, Long userId, boolean isLike) {
        String key = postId + ":" + userId;
        likeOperations.put(key, isLike);
    }
    
    // 获取总操作数量
    public int getTotalOperationCount() {
        return likeOperations.size() + favoriteOperations.size();
    }
}
```

### 2. 批量刷写到数据库

```java
@Transactional
public void flushBatchToDatabase() {
    // 1. 获取当前缓冲的任务（线程安全）
    RelationBatchTask currentTask;
    synchronized (batchTask) {
        if (!batchTask.hasPendingOperations()) {
            return;
        }
        
        // 创建快照，避免在写入过程中新操作影响
        currentTask = new RelationBatchTask();
        currentTask.getLikeOperations().putAll(batchTask.getLikeOperations());
        currentTask.getFavoriteOperations().putAll(batchTask.getFavoriteOperations());
        
        // 清空原任务
        batchTask.clear();
    }
    
    // 2. 异步执行批量写入
    asyncSQLWrapper.executeAsyncVoid(() -> {
        try {
            flushLikesToDatabase(currentTask);
            flushFavoritesToDatabase(currentTask);
        } catch (Exception e) {
            log.error("Failed to flush batch to database", e);
            // 失败时重新放回缓冲队列（补偿机制）
            synchronized (batchTask) {
                batchTask.getLikeOperations().putAll(currentTask.getLikeOperations());
                batchTask.getFavoriteOperations().putAll(currentTask.getFavoriteOperations());
            }
        }
    });
}
```

### 3. 批量写入点赞操作

```java
private void flushLikesToDatabase(RelationBatchTask task) {
    // 1. 分离点赞和取消点赞操作
    List<PostLike> likeCandidates = new ArrayList<>();
    List<PostLike> toDelete = new ArrayList<>();
    
    for (Map.Entry<String, Boolean> entry : task.getLikeOperations().entrySet()) {
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
    
    // 2. 批量查询已存在的点赞关系（避免重复插入）
    Set<String> existingLikes = new HashSet<>();
    if (!likeCandidates.isEmpty()) {
        List<PostLike> existing = postLikeMapper.batchExists(likeCandidates);
        for (PostLike like : existing) {
            existingLikes.add(like.getPostId() + ":" + like.getUserId());
        }
    }
    
    // 3. 过滤出需要插入的点赞关系
    List<PostLike> toInsert = likeCandidates.stream()
        .filter(candidate -> {
            String key = candidate.getPostId() + ":" + candidate.getUserId();
            return !existingLikes.contains(key);
        })
        .collect(Collectors.toList());
    
    // 4. 批量插入点赞
    if (!toInsert.isEmpty()) {
        postLikeMapper.batchInsert(toInsert);
    }
    
    // 5. 批量删除取消点赞
    for (PostLike like : toDelete) {
        postLikeMapper.delete(like.getPostId(), like.getUserId());
    }
}
```

## 优点

1. **性能优秀**
   - 写入缓存速度快，立即返回
   - 批量写入数据库，减少数据库交互次数
   - 性能提升 10-100 倍

2. **高可用性**
   - 缓存故障不影响写入（可以降级到直接写数据库）
   - 数据库故障不影响读取（可以从缓存读取）

3. **可扩展性**
   - 可以水平扩展缓存
   - 可以异步批量写入数据库

4. **用户体验好**
   - 响应速度快，不阻塞用户操作
   - 支持高并发写入

## 缺点

1. **数据一致性**
   - 缓存和数据库可能存在短暂不一致
   - 需要接受最终一致性

2. **数据丢失风险**
   - 缓存故障可能导致数据丢失
   - 需要补偿机制

3. **实现复杂**
   - 需要处理批量合并、去重、补偿等逻辑
   - 需要监控和告警机制

4. **内存消耗**
   - 缓冲队列占用内存
   - 需要控制缓冲大小

## 可能存在的问题及解决方案

### 问题 1：数据丢失

**问题描述：**
- 缓存故障时，缓冲队列中的数据可能丢失
- 服务重启时，内存中的数据可能丢失

**解决方案：**
1. **使用持久化队列**
```java
// 使用 Redis 作为持久化队列
public void persistLike(Long userId, Long postId, boolean isLike) {
    // 1. 写入 Redis 缓存
    updateRedisLike(postId, userId, isLike);
    
    // 2. 写入 Redis 队列（持久化）
    String queueKey = "batch:like:queue";
    LikeOperation op = LikeOperation.builder()
        .postId(postId)
        .userId(userId)
        .isLike(isLike)
        .timestamp(System.currentTimeMillis())
        .build();
    redisTemplate.opsForList().rightPush(queueKey, JSON.toJSONString(op));
    
    // 3. 检查队列长度，达到阈值触发批量写入
    Long queueLength = redisTemplate.opsForList().size(queueKey);
    if (queueLength >= BATCH_SIZE_THRESHOLD) {
        flushBatchToDatabase();
    }
}
```

2. **补偿机制**
```java
// 服务启动时，从 Redis 队列恢复数据
@PostConstruct
public void recoverFromQueue() {
    String queueKey = "batch:like:queue";
    Long queueLength = redisTemplate.opsForList().size(queueKey);
    
    if (queueLength > 0) {
        log.info("Recovering {} operations from queue", queueLength);
        flushBatchToDatabase();
    }
}
```

### 问题 2：数据不一致

**问题描述：**
- 缓存和数据库可能存在短暂不一致
- 用户可能看到不一致的数据

**解决方案：**
1. **缓存过期策略**
```java
// 设置缓存过期时间，定期从数据库刷新
public void updateRedisLike(Long postId, Long userId, boolean isLike) {
    String key = "post:like:" + postId;
    if (isLike) {
        redisTemplate.opsForSet().add(key, userId);
    } else {
        redisTemplate.opsForSet().remove(key, userId);
    }
    // 设置过期时间，定期从数据库刷新
    redisTemplate.expire(key, 1, TimeUnit.HOURS);
}
```

2. **双写一致性**
```java
// 关键操作使用双写策略
public void persistCriticalLike(Long userId, Long postId, boolean isLike) {
    // 1. 同步写入数据库（关键数据）
    if (isLike) {
        postLikeMapper.insert(PostLike.builder()
            .postId(postId)
            .userId(userId)
            .build());
    } else {
        postLikeMapper.delete(postId, userId);
    }
    
    // 2. 更新缓存
    updateRedisLike(postId, userId, isLike);
}
```

### 问题 3：批量写入失败

**问题描述：**
- 批量写入数据库失败时，数据可能丢失
- 需要重试机制

**解决方案：**
```java
// 批量写入失败重试
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
public void flushBatchToDatabase() {
    RelationBatchTask currentTask;
    synchronized (batchTask) {
        if (!batchTask.hasPendingOperations()) {
            return;
        }
        currentTask = new RelationBatchTask();
        currentTask.getLikeOperations().putAll(batchTask.getLikeOperations());
        currentTask.getFavoriteOperations().putAll(batchTask.getFavoriteOperations());
        batchTask.clear();
    }
    
    try {
        flushLikesToDatabase(currentTask);
        flushFavoritesToDatabase(currentTask);
    } catch (Exception e) {
        log.error("Failed to flush batch to database", e);
        // 失败时重新放回缓冲队列
        synchronized (batchTask) {
            batchTask.getLikeOperations().putAll(currentTask.getLikeOperations());
            batchTask.getFavoriteOperations().putAll(currentTask.getFavoriteOperations());
        }
        throw e; // 触发重试
    }
}
```

### 问题 4：内存溢出

**问题描述：**
- 缓冲队列过大可能导致内存溢出
- 需要控制缓冲大小

**解决方案：**
```java
// 限制缓冲队列大小
private static final int MAX_BUFFER_SIZE = 10000;

public void persistLike(Long userId, Long postId, boolean isLike) {
    // 检查缓冲队列大小
    if (batchTask.getTotalOperationCount() >= MAX_BUFFER_SIZE) {
        // 强制刷新，避免内存溢出
        flushBatchToDatabase();
    }
    
    batchTask.addLikeOperation(postId, userId, isLike);
    
    if (batchTask.getTotalOperationCount() >= BATCH_SIZE_THRESHOLD) {
        flushBatchToDatabase();
    } else {
        scheduleBatchFlushTask();
    }
}
```

## 最佳实践

1. **双重触发机制**
   - 定时触发：确保定期写入，避免数据积压
   - 定量触发：高峰期快速响应，避免延迟过长

2. **批量大小控制**
   - 小批量（< 100 条）：直接写入
   - 中批量（100-1000 条）：推荐批量写入
   - 大批量（> 1000 条）：分批写入，每批 500-1000 条

3. **去重合并**
   - 同一 key 的多次操作只保留最新状态
   - 减少无效写入

4. **错误处理**
   - 批量写入失败时，重新放回缓冲队列
   - 使用重试机制
   - 记录失败日志，支持人工介入

5. **监控告警**
   - 监控缓冲队列大小
   - 监控批量写入频率和耗时
   - 监控失败率和重试次数

## 性能对比

| 写入方式 | 响应时间 | 吞吐量 | 数据库压力 |
|---------|---------|--------|-----------|
| 同步写入 | 10-50ms | 100-500 TPS | 高 |
| Write-Behind 批量写入 | 1-5ms | 10000+ TPS | 低 |
| **性能提升** | **2-10倍** | **20-100倍** | **显著降低** |

## 总结

Write-Behind 批量写入策略是高性能系统中常用的缓存写入策略，适用于：
- ✅ 点赞、收藏等高频关系数据写入
- ✅ 计数器批量写入
- ✅ 用户行为日志批量写入
- ✅ 任何高频写入且可以接受最终一致性的场景

**关键要点：**
1. 先写缓存，后写数据库
2. 使用定时+定量双重触发机制
3. 批量合并，去重优化
4. 添加错误处理和补偿机制
5. 监控缓冲队列和批量写入性能

