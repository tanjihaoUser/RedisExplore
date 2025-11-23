# 数据库批量更新（Batch Update）

## 使用情况

**常用程度：⭐⭐⭐⭐（常用）**

批量更新是数据库操作中常用的批量处理方式，适用于需要同时更新多条记录的场景。

## 概述

批量更新是指将多条 UPDATE 语句合并为一次数据库操作，通过减少数据库交互次数来提高更新性能。相比逐条更新，批量更新可以提升 5-50 倍的性能。

## 生产场景示例

### 1. 批量状态更新（Batch Status Update）

**场景描述：**
- 批量更新订单状态、用户状态等
- 定时任务批量处理过期订单
- 批量审核、批量审批操作

**实现示例：**
```java
// 批量更新订单状态
public void batchUpdateOrderStatus(List<Long> orderIds, OrderStatus newStatus) {
    if (orderIds.isEmpty()) {
        return;
    }
    
    // 批量更新
    orderMapper.batchUpdateStatus(orderIds, newStatus);
}

// 使用示例：定时任务批量处理过期订单
@Scheduled(cron = "0 0 1 * * ?") // 每天凌晨1点执行
public void processExpiredOrders() {
    // 查询过期订单
    List<Long> expiredOrderIds = orderMapper.selectExpiredOrders();
    
    // 批量更新为已过期状态
    batchUpdateOrderStatus(expiredOrderIds, OrderStatus.EXPIRED);
}
```

**业界案例：**
- **电商系统**：批量更新订单支付状态、发货状态
- **金融系统**：批量更新交易状态、账户状态
- **内容审核系统**：批量审核内容，更新审核状态

### 2. 批量字段更新（Batch Field Update）

**场景描述：**
- 批量更新用户信息、商品信息等
- 数据迁移、数据修复
- 批量修改配置信息

**实现示例：**
```java
// 批量更新用户信息
public void batchUpdateUsers(List<UserUpdateDTO> updates) {
    if (updates.isEmpty()) {
        return;
    }
    
    // 分批更新，避免 SQL 过长
    int batchSize = 1000;
    for (int i = 0; i < updates.size(); i += batchSize) {
        int end = Math.min(i + batchSize, updates.size());
        List<UserUpdateDTO> batch = updates.subList(i, end);
        userMapper.batchUpdate(batch);
    }
}

// 使用示例：批量修改用户昵称
public void batchUpdateNicknames(Map<Long, String> userIdToNickname) {
    List<UserUpdateDTO> updates = userIdToNickname.entrySet().stream()
        .map(entry -> UserUpdateDTO.builder()
            .userId(entry.getKey())
            .nickname(entry.getValue())
            .build())
        .collect(Collectors.toList());
    
    batchUpdateUsers(updates);
}
```

**业界案例：**
- **社交平台**：批量更新用户头像、昵称
- **电商平台**：批量更新商品价格、库存
- **CRM系统**：批量更新客户信息、标签

### 3. 批量计数器更新（Batch Counter Update）

**场景描述：**
- 批量更新点赞数、评论数、浏览数等计数器
- 定时任务批量同步统计数据
- 数据修复、数据补偿

**实现示例：**
```java
// 批量更新帖子点赞数
public void batchUpdateLikeCount(Map<Long, Integer> postIdToCount) {
    if (postIdToCount.isEmpty()) {
        return;
    }
    
    // 使用增量更新，避免并发问题
    List<PostCounterUpdate> updates = postIdToCount.entrySet().stream()
        .map(entry -> PostCounterUpdate.builder()
            .postId(entry.getKey())
            .likeCountDelta(entry.getValue())
            .build())
        .collect(Collectors.toList());
    
    postMapper.batchIncrementLikeCount(updates);
}

// 使用示例：定时任务批量同步点赞数
@Scheduled(fixedDelay = 300000) // 每5分钟执行一次
public void syncLikeCounts() {
    // 从 Redis 读取所有帖子的点赞数
    Map<Long, Integer> redisCounts = redisService.getAllPostLikeCounts();
    
    // 批量更新到数据库
    batchUpdateLikeCount(redisCounts);
}
```

**业界案例：**
- **微博**：批量同步转发数、评论数、点赞数
- **B站**：批量同步视频播放数、弹幕数
- **GitHub**：批量同步仓库 Star 数、Fork 数

### 4. 批量时间戳更新（Batch Timestamp Update）

**场景描述：**
- 批量更新最后访问时间、最后修改时间等
- 用户活跃度统计
- 数据同步时间戳

**实现示例：**
```java
// 批量更新最后访问时间
public void batchUpdateLastAccessTime(List<Long> userIds) {
    if (userIds.isEmpty()) {
        return;
    }
    
    long currentTime = System.currentTimeMillis();
    userMapper.batchUpdateLastAccessTime(userIds, currentTime);
}

// 使用示例：用户登录后更新访问时间
public void onUserLogin(Long userId) {
    // 异步批量更新（延迟写入）
    asyncUpdateLastAccessTime(userId);
}

// 批量更新访问时间（Write-Behind 策略）
private final List<Long> pendingUserIds = new CopyOnWriteArrayList<>();

private void asyncUpdateLastAccessTime(Long userId) {
    pendingUserIds.add(userId);
    
    // 达到阈值或定时触发批量更新
    if (pendingUserIds.size() >= 100) {
        List<Long> toUpdate = new ArrayList<>(pendingUserIds);
        pendingUserIds.clear();
        batchUpdateLastAccessTime(toUpdate);
    }
}
```

**业界案例：**
- **社交平台**：批量更新用户最后活跃时间
- **内容平台**：批量更新内容最后修改时间
- **电商平台**：批量更新商品最后浏览时间

### 5. 批量软删除（Batch Soft Delete）

**场景描述：**
- 批量软删除数据，保留历史记录
- 数据归档、数据清理
- 支持数据恢复

**实现示例：**
```java
// 批量软删除
public void batchSoftDelete(List<Long> ids) {
    if (ids.isEmpty()) {
        return;
    }
    
    long deleteTime = System.currentTimeMillis();
    postMapper.batchSoftDelete(ids, deleteTime);
}

// 使用示例：定时任务清理过期数据
@Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
public void cleanupExpiredData() {
    // 查询30天前的数据
    List<Long> expiredIds = postMapper.selectExpiredIds(30);
    
    // 批量软删除
    batchSoftDelete(expiredIds);
}
```

**业界案例：**
- **内容平台**：批量软删除过期内容
- **日志系统**：批量归档历史日志
- **消息系统**：批量清理过期消息

## MyBatis 实现

### XML 配置方式

```xml
<!-- 批量更新状态 -->
<update id="batchUpdateStatus">
    UPDATE orders
    SET status = #{status}, update_time = NOW()
    WHERE id IN
    <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">
        #{orderId}
    </foreach>
</update>

<!-- 批量更新多个字段 -->
<update id="batchUpdate">
    UPDATE user_base
    <trim prefix="SET" suffixOverrides=",">
        <trim prefix="username = CASE id" suffix="END,">
            <foreach collection="list" item="item">
                WHEN #{item.userId} THEN #{item.username}
            </foreach>
        </trim>
        <trim prefix="email = CASE id" suffix="END,">
            <foreach collection="list" item="item">
                WHEN #{item.userId} THEN #{item.email}
            </foreach>
        </trim>
    </trim>
    WHERE id IN
    <foreach collection="list" item="item" open="(" separator="," close=")">
        #{item.userId}
    </foreach>
</update>

<!-- 批量增量更新计数器 -->
<update id="batchIncrementLikeCount">
    UPDATE posts
    SET like_count = like_count + CASE id
    <foreach collection="list" item="item">
        WHEN #{item.postId} THEN #{item.likeCountDelta}
    </foreach>
    END,
    update_time = NOW()
    WHERE id IN
    <foreach collection="list" item="item" open="(" separator="," close=")">
        #{item.postId}
    </foreach>
</update>

<!-- 批量软删除 -->
<update id="batchSoftDelete">
    UPDATE posts
    SET deleted = 1, delete_time = #{deleteTime}, update_time = NOW()
    WHERE id IN
    <foreach collection="ids" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
    AND deleted = 0
</update>
```

### 注解方式

```java
@Update({
    "<script>",
    "UPDATE orders SET status = #{status}, update_time = NOW()",
    "WHERE id IN",
    "<foreach collection='orderIds' item='orderId' open='(' separator=',' close=')'>",
    "#{orderId}",
    "</foreach>",
    "</script>"
})
int batchUpdateStatus(@Param("orderIds") List<Long> orderIds, @Param("status") OrderStatus status);
```

### JDBC 批量更新（性能最优）

```java
public void batchUpdateWithJDBC(List<UserUpdateDTO> updates) {
    String sql = "UPDATE user_base SET username = ?, email = ? WHERE id = ?";
    
    try (Connection conn = dataSource.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        conn.setAutoCommit(false);
        
        for (UserUpdateDTO update : updates) {
            pstmt.setString(1, update.getUsername());
            pstmt.setString(2, update.getEmail());
            pstmt.setLong(3, update.getUserId());
            pstmt.addBatch();
            
            // 每 1000 条执行一次
            if (updates.size() % 1000 == 0) {
                pstmt.executeBatch();
                conn.commit();
            }
        }
        
        pstmt.executeBatch();
        conn.commit();
    }
}
```

## 优点

1. **性能优秀**
   - 减少数据库交互次数，从 N 次减少到 1 次
   - 减少网络往返开销
   - 数据库可以优化批量操作，性能提升 5-50 倍

2. **事务效率高**
   - 批量操作在同一个事务中，减少事务开销
   - 减少锁竞争时间

3. **代码简洁**
   - 实现简单，易于维护
   - 支持各种 ORM 框架

## 缺点

1. **行锁竞争**
   - 批量更新可能导致行级锁竞争
   - 影响其他并发操作

2. **事务日志增长**
   - 大批量更新可能导致事务日志快速增长
   - 可能影响数据库性能

3. **错误处理复杂**
   - 批量操作中部分失败时，难以定位具体失败记录
   - 需要额外的错误处理机制

4. **数据一致性**
   - 批量更新时，如果部分记录更新失败，可能导致数据不一致
   - 需要合理的事务管理

## 可能存在的问题及解决方案

### 问题 1：行锁竞争

**问题描述：**
- 批量更新时，对多行加锁
- 可能导致其他事务等待
- 影响并发性能

**解决方案：**
1. **分批更新，减少锁持有时间**
```java
public void batchUpdateWithBatching(List<Long> ids, OrderStatus status) {
    int batchSize = 500; // 每批 500 条
    
    for (int i = 0; i < ids.size(); i += batchSize) {
        int end = Math.min(i + batchSize, ids.size());
        List<Long> batch = ids.subList(i, end);
        
        // 每批单独事务，减少锁持有时间
        transactionTemplate.execute(status -> {
            orderMapper.batchUpdateStatus(batch, status);
            return null;
        });
    }
}
```

2. **在非高峰期执行**
```java
// 在低峰期执行批量更新
@Scheduled(cron = "0 0 2 * * ?") // 凌晨2点执行
public void batchUpdateInLowTraffic() {
    // 批量更新操作
}
```

3. **使用乐观锁**
```java
// 使用版本号实现乐观锁
UPDATE orders
SET status = #{status}, version = version + 1
WHERE id = #{id} AND version = #{oldVersion}
```

### 问题 2：SQL 语句过长

**问题描述：**
- 批量更新时，SQL 语句可能过长
- 可能超过数据库的 SQL 长度限制

**解决方案：**
```java
// 分批更新，控制每批大小
private static final int BATCH_SIZE = 1000;

public void batchUpdate(List<UserUpdateDTO> updates) {
    for (int i = 0; i < updates.size(); i += BATCH_SIZE) {
        int end = Math.min(i + BATCH_SIZE, updates.size());
        List<UserUpdateDTO> batch = updates.subList(i, end);
        userMapper.batchUpdate(batch);
    }
}
```

### 问题 3：部分更新失败

**问题描述：**
- 批量更新时，部分记录可能更新失败
- 导致数据不一致
- 难以定位失败记录

**解决方案：**
1. **事务管理**
```java
@Transactional(rollbackFor = Exception.class)
public void batchUpdateWithTransaction(List<UserUpdateDTO> updates) {
    try {
        userMapper.batchUpdate(updates);
    } catch (Exception e) {
        log.error("Batch update failed", e);
        throw e; // 回滚整个事务
    }
}
```

2. **逐条更新，记录失败**
```java
public BatchUpdateResult batchUpdateWithErrorHandling(List<UserUpdateDTO> updates) {
    List<Long> successIds = new ArrayList<>();
    List<Long> failureIds = new ArrayList<>();
    
    for (UserUpdateDTO update : updates) {
        try {
            userMapper.update(update);
            successIds.add(update.getUserId());
        } catch (Exception e) {
            log.error("Failed to update user {}", update.getUserId(), e);
            failureIds.add(update.getUserId());
        }
    }
    
    return BatchUpdateResult.builder()
        .successIds(successIds)
        .failureIds(failureIds)
        .build();
}
```

3. **使用重试机制**
```java
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
public void batchUpdateWithRetry(List<UserUpdateDTO> updates) {
    userMapper.batchUpdate(updates);
}
```

### 问题 4：并发更新冲突

**问题描述：**
- 多个线程同时更新同一条记录
- 可能导致数据覆盖
- 最后写入获胜（Lost Update）

**解决方案：**
1. **使用增量更新**
```java
// 使用增量更新，避免覆盖
UPDATE posts
SET like_count = like_count + #{delta}
WHERE id = #{postId}
```

2. **使用版本号**
```java
// 使用版本号控制并发
UPDATE orders
SET status = #{status}, version = version + 1
WHERE id = #{id} AND version = #{oldVersion}
```

3. **使用分布式锁**
```java
// 使用 Redis 分布式锁
public void batchUpdateWithLock(List<Long> ids, OrderStatus status) {
    String lockKey = "batch_update_lock";
    if (redisLock.tryLock(lockKey, 10, TimeUnit.SECONDS)) {
        try {
            orderMapper.batchUpdateStatus(ids, status);
        } finally {
            redisLock.unlock(lockKey);
        }
    }
}
```

### 问题 5：性能监控

**问题描述：**
- 批量更新性能难以监控
- 不知道哪些批量操作慢

**解决方案：**
```java
public void batchUpdate(List<UserUpdateDTO> updates) {
    long startTime = System.currentTimeMillis();
    try {
        userMapper.batchUpdate(updates);
        long duration = System.currentTimeMillis() - startTime;
        log.info("Batch update completed: {} records, {}ms, {} records/ms", 
            updates.size(), duration, updates.size() * 1000.0 / duration);
    } catch (Exception e) {
        log.error("Batch update failed: {} records", updates.size(), e);
        throw e;
    }
}
```

## 最佳实践

1. **批量大小控制**
   - 小批量（< 100 条）：直接更新，性能差异不明显
   - 中批量（100-1000 条）：推荐批量更新
   - 大批量（> 1000 条）：分批更新，每批 500-1000 条

2. **事务管理**
   - 小批量：单事务处理
   - 大批量：分批提交，避免长事务
   - 关键数据：使用事务保证一致性

3. **并发控制**
   - 使用增量更新避免覆盖
   - 使用版本号控制并发
   - 合理使用分布式锁

4. **错误处理**
   - 记录失败批次，支持重试
   - 使用死信队列处理失败数据
   - 提供数据校验机制

5. **性能优化**
   - 关闭自动提交，手动提交事务
   - 使用 JDBC 批量更新（性能最优）
   - 合理设置批量大小，平衡内存和性能

## 性能对比

| 操作方式 | 100条记录耗时 | 1000条记录耗时 | 10000条记录耗时 |
|---------|-------------|--------------|---------------|
| 逐条更新 | 200-500ms | 2000-5000ms | 20000-50000ms |
| 批量更新 | 10-50ms | 50-200ms | 500-2000ms |
| **性能提升** | **4-50倍** | **10-100倍** | **10-100倍** |

## 总结

批量更新是数据库操作中常用的批量处理方式，适用于：
- ✅ 批量状态更新
- ✅ 批量字段更新
- ✅ 批量计数器更新
- ✅ 批量时间戳更新
- ✅ 批量软删除
- ✅ 任何需要批量更新数据的场景

**关键要点：**
1. 控制批量大小（500-1000 条/批）
2. 分批提交事务，避免长事务
3. 使用增量更新避免并发冲突
4. 添加性能监控和错误处理
5. 根据业务场景选择合适的批量大小和更新策略

