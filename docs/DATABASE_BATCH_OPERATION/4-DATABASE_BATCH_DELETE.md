# 数据库批量删除（Batch Delete）

## 使用情况

**常用程度：⭐⭐⭐⭐（常用）**

批量删除是数据库操作中常用的批量处理方式，适用于需要同时删除多条记录的场景。

## 概述

批量删除是指将多条 DELETE 语句合并为一次数据库操作，通过减少数据库交互次数来提高删除性能。相比逐条删除，批量删除可以提升 5-50 倍的性能。

## 生产场景示例

### 1. 批量物理删除（Batch Physical Delete）

**场景描述：**
- 批量删除临时数据、测试数据
- 数据清理、数据归档
- 彻底删除不需要的数据

**实现示例：**
```java
// 批量物理删除
public void batchDelete(List<Long> ids) {
    if (ids.isEmpty()) {
        return;
    }
    
    // 分批删除，避免 SQL 过长
    int batchSize = 1000;
    for (int i = 0; i < ids.size(); i += batchSize) {
        int end = Math.min(i + batchSize, ids.size());
        List<Long> batch = ids.subList(i, end);
        postMapper.batchDelete(batch);
    }
}

// 使用示例：定时任务清理临时数据
@Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点执行
public void cleanupTempData() {
    // 查询7天前的临时数据
    List<Long> tempIds = postMapper.selectTempDataOlderThan(7);
    
    // 批量删除
    batchDelete(tempIds);
}
```

**业界案例：**
- **日志系统**：批量删除过期日志
- **缓存系统**：批量清理过期缓存数据
- **临时文件系统**：批量删除临时文件记录

### 2. 批量软删除（Batch Soft Delete）

**场景描述：**
- 批量软删除数据，保留历史记录
- 支持数据恢复
- 数据归档、数据清理

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

// 使用示例：用户批量删除自己的内容
public void batchDeleteUserPosts(Long userId, List<Long> postIds) {
    // 验证权限：只能删除自己的内容
    List<Post> posts = postMapper.selectByIds(postIds);
    boolean allOwned = posts.stream()
        .allMatch(post -> post.getAuthorId().equals(userId));
    
    if (!allOwned) {
        throw new IllegalArgumentException("Cannot delete posts that are not yours");
    }
    
    // 批量软删除
    batchSoftDelete(postIds);
}
```

**业界案例：**
- **内容平台**：用户批量删除自己的内容
- **电商平台**：批量删除过期商品
- **社交平台**：批量删除违规内容

### 3. 级联删除（Cascade Delete）

**场景描述：**
- 删除主表记录时，同时删除关联的子表记录
- 保证数据一致性
- 避免孤立数据

**实现示例：**
```java
// 级联删除：删除帖子时，同时删除点赞、评论等关联数据
@Transactional
public void cascadeDeletePost(Long postId) {
    // 1. 删除点赞记录
    postLikeMapper.deleteByPostId(postId);
    
    // 2. 删除收藏记录
    postFavoriteMapper.deleteByPostId(postId);
    
    // 3. 删除评论记录
    commentMapper.deleteByPostId(postId);
    
    // 4. 删除帖子本身
    postMapper.delete(postId);
}

// 批量级联删除
@Transactional
public void batchCascadeDeletePosts(List<Long> postIds) {
    if (postIds.isEmpty()) {
        return;
    }
    
    // 批量删除关联数据
    postLikeMapper.batchDeleteByPostIds(postIds);
    postFavoriteMapper.batchDeleteByPostIds(postIds);
    commentMapper.batchDeleteByPostIds(postIds);
    
    // 批量删除帖子
    postMapper.batchDelete(postIds);
}
```

**业界案例：**
- **内容平台**：删除帖子时，级联删除所有关联数据
- **电商平台**：删除商品时，级联删除库存、价格等数据
- **社交平台**：删除用户时，级联删除用户的所有内容

### 4. 条件批量删除（Conditional Batch Delete）

**场景描述：**
- 根据条件批量删除数据
- 数据清理、数据归档
- 定时任务批量处理

**实现示例：**
```java
// 根据条件批量删除
public void batchDeleteByCondition(DeleteCondition condition) {
    // 先查询符合条件的记录
    List<Long> ids = postMapper.selectByCondition(condition);
    
    if (ids.isEmpty()) {
        return;
    }
    
    // 批量删除
    batchDelete(ids);
}

// 使用示例：删除30天前未激活的用户
@Scheduled(cron = "0 0 4 * * ?") // 每天凌晨4点执行
public void cleanupInactiveUsers() {
    DeleteCondition condition = DeleteCondition.builder()
        .status(UserStatus.INACTIVE)
        .createdBefore(LocalDateTime.now().minusDays(30))
        .build();
    
    batchDeleteByCondition(condition);
}
```

**业界案例：**
- **用户系统**：批量删除长期未激活的用户
- **订单系统**：批量删除已取消的订单
- **日志系统**：批量删除过期的日志记录

### 5. 分批删除（Batch Delete with Pagination）

**场景描述：**
- 大批量删除时，分批处理避免长时间锁表
- 减少对数据库的影响
- 支持中断和恢复

**实现示例：**
```java
// 分批删除，避免长时间锁表
public void batchDeleteWithPagination(Long maxId, int batchSize) {
    while (true) {
        // 查询一批需要删除的记录
        List<Long> ids = postMapper.selectIdsToDelete(maxId, batchSize);
        
        if (ids.isEmpty()) {
            break;
        }
        
        // 批量删除
        postMapper.batchDelete(ids);
        
        // 更新最大ID
        maxId = ids.stream().max(Long::compareTo).orElse(maxId);
        
        // 短暂休眠，避免对数据库造成过大压力
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
}

// 使用示例：定时任务分批删除过期数据
@Scheduled(fixedDelay = 3600000) // 每小时执行一次
public void cleanupExpiredDataIncrementally() {
    Long lastProcessedId = redisService.getLastProcessedDeleteId();
    batchDeleteWithPagination(lastProcessedId, 1000);
}
```

**业界案例：**
- **大数据平台**：分批删除历史数据
- **日志系统**：分批清理过期日志
- **内容平台**：分批删除过期内容

## MyBatis 实现

### XML 配置方式

```xml
<!-- 批量物理删除 -->
<delete id="batchDelete">
    DELETE FROM posts
    WHERE id IN
    <foreach collection="ids" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</delete>

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

<!-- 根据条件批量删除 -->
<delete id="batchDeleteByCondition">
    DELETE FROM posts
    WHERE status = #{status}
    AND created_time < #{beforeTime}
    AND deleted = 0
</delete>

<!-- 级联删除：批量删除关联数据 -->
<delete id="batchDeleteByPostIds">
    DELETE FROM post_like
    WHERE post_id IN
    <foreach collection="postIds" item="postId" open="(" separator="," close=")">
        #{postId}
    </foreach>
</delete>
```

### 注解方式

```java
@Delete({
    "<script>",
    "DELETE FROM posts WHERE id IN",
    "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
    "#{id}",
    "</foreach>",
    "</script>"
})
int batchDelete(@Param("ids") List<Long> ids);
```

### JDBC 批量删除（性能最优）

```java
public void batchDeleteWithJDBC(List<Long> ids) {
    String sql = "DELETE FROM posts WHERE id = ?";
    
    try (Connection conn = dataSource.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        conn.setAutoCommit(false);
        
        for (Long id : ids) {
            pstmt.setLong(1, id);
            pstmt.addBatch();
            
            // 每 1000 条执行一次
            if (ids.size() % 1000 == 0) {
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

1. **表锁风险**
   - 大批量删除可能导致表锁
   - 影响其他并发操作

2. **表碎片化**
   - 大规模删除可能导致表碎片化
   - 影响查询性能

3. **事务日志增长**
   - 大批量删除可能导致事务日志快速增长
   - 可能影响数据库性能

4. **数据恢复困难**
   - 物理删除后数据难以恢复
   - 需要额外的备份机制

## 可能存在的问题及解决方案

### 问题 1：表锁和性能影响

**问题描述：**
- 大批量删除可能导致表锁
- 影响其他查询和更新操作
- 可能导致数据库性能下降

**解决方案：**
1. **分批删除，减少锁持有时间**
```java
public void batchDeleteWithBatching(List<Long> ids) {
    int batchSize = 500; // 每批 500 条
    
    for (int i = 0; i < ids.size(); i += batchSize) {
        int end = Math.min(i + batchSize, ids.size());
        List<Long> batch = ids.subList(i, end);
        
        // 每批单独事务，减少锁持有时间
        transactionTemplate.execute(status -> {
            postMapper.batchDelete(batch);
            return null;
        });
        
        // 短暂休眠，避免对数据库造成过大压力
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
}
```

2. **在非高峰期执行**
```java
// 在低峰期执行批量删除
@Scheduled(cron = "0 0 3 * * ?") // 凌晨3点执行
public void batchDeleteInLowTraffic() {
    // 批量删除操作
}
```

3. **使用软删除代替物理删除**
```java
// 使用软删除，避免表锁
public void batchSoftDelete(List<Long> ids) {
    long deleteTime = System.currentTimeMillis();
    postMapper.batchSoftDelete(ids, deleteTime);
    
    // 定期物理删除已软删除的数据
    schedulePhysicalDelete();
}
```

### 问题 2：表碎片化

**问题描述：**
- 大规模删除后，表可能出现碎片化
   - 影响查询性能
   - 占用额外存储空间

**解决方案：**
1. **定期优化表**
```java
// 删除后优化表
public void batchDeleteAndOptimize(List<Long> ids) {
    // 批量删除
    postMapper.batchDelete(ids);
    
    // 优化表（重建索引、整理碎片）
    // 注意：OPTIMIZE TABLE 会锁表，需要在低峰期执行
    jdbcTemplate.execute("OPTIMIZE TABLE posts");
}
```

2. **使用 ALTER TABLE 重建表**
```sql
-- 重建表，整理碎片
ALTER TABLE posts ENGINE=InnoDB;
```

3. **定期维护**
```java
// 定期维护任务
@Scheduled(cron = "0 0 5 * * 0") // 每周日凌晨5点执行
public void weeklyMaintenance() {
    // 优化表
    jdbcTemplate.execute("OPTIMIZE TABLE posts");
    
    // 分析表
    jdbcTemplate.execute("ANALYZE TABLE posts");
}
```

### 问题 3：SQL 语句过长

**问题描述：**
- 批量删除时，SQL 语句可能过长
- 可能超过数据库的 SQL 长度限制

**解决方案：**
```java
// 分批删除，控制每批大小
private static final int BATCH_SIZE = 1000;

public void batchDelete(List<Long> ids) {
    for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
        int end = Math.min(i + BATCH_SIZE, ids.size());
        List<Long> batch = ids.subList(i, end);
        postMapper.batchDelete(batch);
    }
}
```

### 问题 4：数据恢复

**问题描述：**
- 物理删除后数据难以恢复
- 误删除可能导致数据丢失

**解决方案：**
1. **使用软删除**
```java
// 使用软删除，支持数据恢复
public void batchSoftDelete(List<Long> ids) {
    long deleteTime = System.currentTimeMillis();
    postMapper.batchSoftDelete(ids, deleteTime);
}

// 恢复软删除的数据
public void batchRestore(List<Long> ids) {
    postMapper.batchRestore(ids);
}
```

2. **删除前备份**
```java
// 删除前备份数据
public void batchDeleteWithBackup(List<Long> ids) {
    // 1. 备份数据
    List<Post> posts = postMapper.selectByIds(ids);
    backupService.backup(posts);
    
    // 2. 批量删除
    postMapper.batchDelete(ids);
}
```

3. **使用数据库备份**
```java
// 删除前创建数据库快照
public void batchDeleteWithSnapshot(List<Long> ids) {
    // 1. 创建数据库快照
    databaseService.createSnapshot();
    
    // 2. 批量删除
    postMapper.batchDelete(ids);
    
    // 3. 如果需要恢复，可以从快照恢复
}
```

### 问题 5：级联删除性能

**问题描述：**
- 级联删除时，需要删除多个表的记录
- 可能导致性能问题
- 事务时间过长

**解决方案：**
1. **优化删除顺序**
```java
// 先删除子表，再删除主表（避免外键约束问题）
@Transactional
public void cascadeDeletePostOptimized(Long postId) {
    // 1. 删除点赞记录（数据量小）
    postLikeMapper.deleteByPostId(postId);
    
    // 2. 删除收藏记录（数据量小）
    postFavoriteMapper.deleteByPostId(postId);
    
    // 3. 分批删除评论记录（数据量大）
    batchDeleteCommentsByPostId(postId);
    
    // 4. 删除帖子本身
    postMapper.delete(postId);
}
```

2. **使用数据库级联删除**
```sql
-- 在数据库层面设置级联删除
ALTER TABLE post_like
ADD CONSTRAINT fk_post_like_post
FOREIGN KEY (post_id) REFERENCES posts(id)
ON DELETE CASCADE;
```

3. **异步删除**
```java
// 异步删除，不阻塞主流程
@Async
public CompletableFuture<Void> cascadeDeletePostAsync(Long postId) {
    cascadeDeletePost(postId);
    return CompletableFuture.completedFuture(null);
}
```

## 最佳实践

1. **优先使用软删除**
   - 支持数据恢复
   - 避免表锁和碎片化
   - 保留历史记录

2. **批量大小控制**
   - 小批量（< 100 条）：直接删除
   - 中批量（100-1000 条）：推荐批量删除
   - 大批量（> 1000 条）：分批删除，每批 500-1000 条

3. **事务管理**
   - 小批量：单事务处理
   - 大批量：分批提交，避免长事务
   - 级联删除：使用事务保证一致性

4. **错误处理**
   - 记录失败批次，支持重试
   - 删除前备份数据
   - 提供数据恢复机制

5. **性能优化**
   - 在非高峰期执行大批量删除
   - 分批删除，减少锁持有时间
   - 定期优化表，整理碎片

## 性能对比

| 操作方式 | 100条记录耗时 | 1000条记录耗时 | 10000条记录耗时 |
|---------|-------------|--------------|---------------|
| 逐条删除 | 200-500ms | 2000-5000ms | 20000-50000ms |
| 批量删除 | 10-50ms | 50-200ms | 500-2000ms |
| **性能提升** | **4-50倍** | **10-100倍** | **10-100倍** |

## 软删除 vs 物理删除

### 软删除（推荐）

**优点：**
- ✅ 支持数据恢复
- ✅ 避免表锁和碎片化
- ✅ 保留历史记录
- ✅ 性能影响小

**缺点：**
- ❌ 占用存储空间
- ❌ 查询时需要过滤已删除数据
- ❌ 需要定期清理

### 物理删除

**优点：**
- ✅ 彻底删除，不占用空间
- ✅ 查询性能好（不需要过滤）

**缺点：**
- ❌ 数据无法恢复
- ❌ 可能导致表锁
- ❌ 可能导致表碎片化

## 总结

批量删除是数据库操作中常用的批量处理方式，适用于：
- ✅ 批量物理删除
- ✅ 批量软删除
- ✅ 级联删除
- ✅ 条件批量删除
- ✅ 分批删除
- ✅ 任何需要批量删除数据的场景

**关键要点：**
1. **优先使用软删除**，支持数据恢复
2. 控制批量大小（500-1000 条/批）
3. 分批提交事务，避免长事务
4. 在非高峰期执行大批量删除
5. 定期优化表，整理碎片
6. 删除前备份数据，提供恢复机制

