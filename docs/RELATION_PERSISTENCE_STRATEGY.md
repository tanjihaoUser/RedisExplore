# 关系数据持久化策略说明

本文档说明 Redis Set 关系数据（关注、点赞、收藏、黑名单）的持久化策略和实现。

## 业界常见做法

### 1. 写策略分类

#### Write-Through（写透）策略
- **特点**：先写 Redis，立即写数据库，保证强一致性
- **优点**：数据强一致，可靠性高
- **缺点**：响应时间较长，数据库压力大
- **适用场景**：关键业务数据，如关注关系、黑名单

#### Write-Behind（写回）策略  
- **特点**：先写 Redis，异步批量写数据库，保证最终一致性
- **优点**：响应速度快，数据库压力小
- **缺点**：存在数据丢失风险（Redis 崩溃时）
- **适用场景**：高频操作，可接受短暂不一致，如点赞、收藏

#### Cache-Aside（旁路缓存）策略
- **特点**：先写数据库，再删除缓存
- **优点**：实现简单
- **缺点**：读多写少场景，写操作会删除缓存影响后续读取性能
- **适用场景**：读多写少的场景

## 本项目的持久化策略

### 关注关系（Follow）- Write-Through

**策略**：立即写入数据库

**原因**：
- 关注关系是核心用户关系数据，重要性高
- 需要保证数据强一致性
- 关注操作频率相对较低，可以接受同步写入的性能开销

**实现**：
```java
// RelationServiceImpl.follow()
1. 使用 Lua 脚本原子性更新 Redis Set
2. 立即调用 persistenceService.persistFollow() 写入数据库
3. 如果数据库写入失败，记录日志但不抛出异常（Redis已成功）
```

**优势**：
- 数据可靠性高，Redis 崩溃时数据库仍有完整数据
- 可用于数据恢复和迁移
- 适合作为权威数据源

### 点赞关系（Like）- Write-Behind

**策略**：异步批量写入数据库

**原因**：
- 点赞操作高频，对实时性要求高
- 可接受短暂的数据不一致（最终一致性）
- 减少数据库压力，提高响应速度

**实现**：
```java
// RelationServiceImpl.likePost()
1. 使用 Lua 脚本原子性更新 Redis Set
2. 异步调用 persistenceService.persistLike()（不阻塞主流程）
3. 使用 @Async 注解在独立线程池执行数据库写入
4. 如果数据库写入失败，记录日志但不影响主流程
```

**优势**：
- 响应速度快，用户体验好
- 数据库压力小，支持高并发
- 适合高频操作场景

**风险与应对**：
- **风险**：Redis 崩溃时可能丢失未持久化的数据
- **应对**：
  - 定期批量同步（batchSyncLikes）
  - 可以增加 MQ 机制，将写入任务发送到消息队列，确保可靠性
  - 监控 Redis 状态，及时发现和处理异常

### 收藏关系（Favorite）- Write-Behind

**策略**：异步批量写入数据库

**原因**：类似点赞，高频操作，可接受最终一致性

**实现**：与点赞关系相同

### 黑名单（Block）- Write-Through

**策略**：立即写入数据库

**原因**：
- 黑名单关系重要，需要立即生效
- 保证数据一致性，避免用户看到不应该看到的内容
- 操作频率低，可以接受同步写入

**实现**：与关注关系相同

## 批量同步机制（数据恢复/迁移）

### 使用场景

1. **数据恢复**：Redis 崩溃后，从数据库恢复数据到 Redis
2. **数据迁移**：系统迁移、数据同步
3. **数据校验**：定期校验 Redis 和数据库的一致性
4. **容灾**：主 Redis 故障，切换到备用 Redis 后批量加载数据

### 实现方法

```java
// 批量同步关注关系
persistenceService.batchSyncFollows(userId);
// 从 Redis 读取所有关注关系，批量写入数据库

// 批量同步点赞关系
persistenceService.batchSyncLikes(postId);
// 从 Redis 读取所有点赞用户，批量写入数据库

// 批量同步收藏关系
persistenceService.batchSyncFavorites(userId);
// 从 Redis 读取所有收藏，批量写入数据库
```

## 性能优化建议

### 1. 批量写入优化

对于 Write-Behind 策略，可以进一步优化为批量写入：

```java
// 示例：将多个点赞操作合并为批量写入
List<PostLike> batchLikes = new ArrayList<>();
// 收集一批点赞操作
batchLikes.add(new PostLike(userId, postId));
// 定时批量写入数据库（如每 100 条或每 10 秒）
if (batchLikes.size() >= 100) {
    postLikeMapper.batchInsert(batchLikes);
    batchLikes.clear();
}
```

### 2. 异步线程池配置

```yaml
# application.yml
spring:
  task:
    execution:
      pool:
        core-size: 5
        max-size: 20
        queue-capacity: 200
      thread-name-prefix: relation-persist-
```

### 3. 监控和告警

- 监控数据库写入延迟
- 监控异步任务队列大小
- 监控 Redis 和数据库的一致性
- 设置告警，及时发现数据不一致

### 4. 补偿机制

对于异步写入失败，可以通过以下方式补偿：

1. **消息队列**：将写入任务发送到 MQ，确保可靠性
2. **定时任务**：定期扫描 Redis，同步到数据库
3. **重试机制**：异步写入失败时自动重试

## 数据库表设计建议

### 关注关系表（user_follow）

```sql
CREATE TABLE user_follow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    follower_id BIGINT NOT NULL COMMENT '关注者ID',
    followed_id BIGINT NOT NULL COMMENT '被关注者ID',
    created_at BIGINT NOT NULL COMMENT '关注时间',
    UNIQUE KEY uk_follower_followed (follower_id, followed_id),
    INDEX idx_follower (follower_id),
    INDEX idx_followed (followed_id)
) COMMENT '用户关注关系表';
```

### 点赞关系表（post_like）

```sql
CREATE TABLE post_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL COMMENT '帖子ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    created_at BIGINT NOT NULL COMMENT '点赞时间',
    UNIQUE KEY uk_post_user (post_id, user_id),
    INDEX idx_post (post_id),
    INDEX idx_user (user_id)
) COMMENT '帖子点赞关系表';
```

### 收藏关系表（post_favorite）

```sql
CREATE TABLE post_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    post_id BIGINT NOT NULL COMMENT '帖子ID',
    created_at BIGINT NOT NULL COMMENT '收藏时间',
    UNIQUE KEY uk_user_post (user_id, post_id),
    INDEX idx_user (user_id),
    INDEX idx_post (post_id)
) COMMENT '帖子收藏关系表';
```

## 数据一致性保障

### 1. Redis 优先策略

- Redis 作为主要数据源，保证读写性能
- 数据库作为备份，保证数据持久化
- 读操作优先从 Redis 读取

### 2. 最终一致性

- Write-Behind 策略保证最终一致性
- 通过批量同步机制弥补数据差异
- 定期校验数据一致性

### 3. 容灾机制

- Redis 崩溃时，可以从数据库恢复数据
- 数据库故障时，Redis 仍可提供服务（短暂不一致可接受）
- 支持主从切换和集群模式

## 总结

本项目采用**混合持久化策略**：

- **Write-Through（写透）**：关注关系、黑名单 → 强一致性，可靠性高
- **Write-Behind（写回）**：点赞、收藏 → 高性能，最终一致性

这种策略平衡了**性能**、**一致性**和**可靠性**，适合社交媒体的高频读写场景。


