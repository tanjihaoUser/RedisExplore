# 数据库批量插入（Batch Insert）

## 使用情况

**常用程度：⭐⭐⭐⭐⭐（最常用）**

批量插入是数据库操作中最常用的批量处理方式，几乎所有的批量数据处理场景都会用到。



## 概述

批量插入是指将多条 INSERT 语句合并为一次数据库操作，通过减少数据库交互次数来提高插入性能。相比逐条插入，批量插入可以提升 10-100 倍的性能。



## 生产场景示例

### 1. 日志记录（Logging）

**场景描述：**
- 系统需要记录用户操作日志、系统日志等
- 日志量巨大，每秒可能有数千条记录
- 需要快速写入，不能阻塞主业务流程

**实现示例：**
```java
// 日志批量插入
public void batchInsertLogs(List<OperationLog> logs) {
    if (logs.isEmpty()) return;
    
    // 批量插入，每批最多 1000 条
    int batchSize = 1000;
    for (int i = 0; i < logs.size(); i += batchSize) {
        int end = Math.min(i + batchSize, logs.size());
        List<OperationLog> batch = logs.subList(i, end);
        logMapper.batchInsert(batch);
    }
}
```

**业界案例：**
- **淘宝/天猫**：用户行为日志、商品浏览日志批量写入
- **微信**：消息发送日志、用户操作日志批量记录
- **微博**：用户互动日志、内容发布日志批量存储



### 2. 关系数据批量写入（Relation Data）

**场景描述：**
- 用户点赞、收藏、关注等关系数据
- 高频操作，需要快速响应
- 采用 Write-Behind 策略，先写缓存，后批量写入数据库

**实现示例：**
```java
// 点赞关系批量插入
public void batchInsertLikes(List<PostLike> likes) {
    if (likes.isEmpty()) return;
    
    // 先批量查询已存在的记录，避免重复插入
    List<PostLike> existing = postLikeMapper.batchExists(likes);
    Set<String> existingSet = existing.stream()
        .map(l -> l.getPostId() + ":" + l.getUserId())
        .collect(Collectors.toSet());
    
    // 过滤出需要插入的记录
    List<PostLike> toInsert = likes.stream()
        .filter(l -> !existingSet.contains(l.getPostId() + ":" + l.getUserId()))
        .collect(Collectors.toList());
    
    if (!toInsert.isEmpty()) {
        postLikeMapper.batchInsert(toInsert);
    }
}
```

**业界案例：**
- **抖音**：视频点赞、评论点赞批量写入
- **小红书**：笔记收藏、用户关注批量持久化
- **B站**：视频投币、收藏批量写入



### 3. 数据导入/迁移（Data Import/Migration）

**场景描述：**
- 从其他系统导入数据
- 数据库迁移、数据同步
- 一次性导入大量历史数据

**实现示例：**
```java
// 用户数据批量导入
public void batchImportUsers(List<User> users) {
    int batchSize = 5000; // 大批量导入，每批 5000 条
    for (int i = 0; i < users.size(); i += batchSize) {
        int end = Math.min(i + batchSize, users.size());
        List<User> batch = users.subList(i, end);
        
        try {
            userMapper.batchInsert(batch);
            log.info("Imported batch {}-{}", i, end);
        } catch (Exception e) {
            log.error("Failed to import batch {}-{}", i, end, e);
            // 失败重试或记录到失败队列
        }
    }
}
```

**业界案例：**
- **银行系统**：客户数据批量导入
- **电商系统**：商品数据批量导入
- **CRM系统**：客户关系数据批量迁移



### 4. 订单批量创建（Order Creation）

**场景描述：**
- 批量下单、批量创建订单
- 促销活动时大量订单生成
- 需要保证事务一致性

**实现示例：**
```java
@Transactional
public void batchCreateOrders(List<Order> orders) {
    // 批量插入订单主表
    orderMapper.batchInsert(orders);
    
    // 批量插入订单明细
    List<OrderItem> items = orders.stream()
        .flatMap(order -> order.getItems().stream())
        .collect(Collectors.toList());
    orderItemMapper.batchInsert(items);
}
```

**业界案例：**
- **京东**：秒杀活动订单批量创建
- **美团**：团购订单批量生成
- **拼多多**：拼团订单批量处理



## MyBatis 实现

### XML 配置方式

```xml
<!-- 批量插入 -->
<insert id="batchInsert" parameterType="java.util.List">
    INSERT INTO post_like (post_id, user_id, created_at) VALUES
    <foreach collection="list" item="item" separator=",">
        (# {item.postId}, # {item.userId}, # {item.createdAt})
    </foreach>
</insert>
```

### 注解方式

```java
@Insert({
    "<script>",
    "INSERT INTO post_like (post_id, user_id) VALUES",
    "<foreach collection='list' item='item' separator=','>",
    "(#{item.postId}, #{item.userId})",
    "</foreach>",
    "</script>"
})
int batchInsert(@Param("list") List<PostLike> likes);
```

### JDBC 批量插入（性能最优）

```java
public void batchInsertWithJDBC(List<PostLike> likes) {
    String sql = "INSERT INTO post_like (post_id, user_id) VALUES (?, ?)";
    
    try (Connection conn = dataSource.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        conn.setAutoCommit(false);
        
        for (PostLike like : likes) {
            pstmt.setLong(1, like.getPostId());
            pstmt.setLong(2, like.getUserId());
            pstmt.addBatch();
            
            // 每 1000 条执行一次
            if (likes.size() % 1000 == 0) {
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
   - 数据库可以优化批量操作，性能提升 10-100 倍

2. **事务效率高**
   - 批量操作在同一个事务中，减少事务开销
   - 减少锁竞争时间

3. **代码简洁**
   - 实现简单，易于维护
   - 支持各种 ORM 框架



## 缺点

1. **事务日志增长**
   - 大批量插入可能导致事务日志快速增长
   - 可能影响数据库性能

2. **内存消耗**
   - 需要一次性加载大量数据到内存
   - 可能导致内存溢出

3. **错误处理复杂**
   - 批量操作中部分失败时，难以定位具体失败记录
   - 需要额外的错误处理机制

4. **锁竞争**
   - 大批量插入可能导致表锁或行锁竞争
   - 影响其他并发操作



## 可能存在的问题及解决方案

### 问题 1：SQL 语句过长

**问题描述：**
- 批量插入时，SQL 语句可能过长
- MySQL 的 `max_allowed_packet` 限制（默认 4MB）
- 可能导致 SQL 执行失败

**解决方案：**
```java
// 分批处理，控制每批大小
private static final int BATCH_SIZE = 1000;

public void batchInsert(List<PostLike> likes) {
    for (int i = 0; i < likes.size(); i += BATCH_SIZE) {
        int end = Math.min(i + BATCH_SIZE, likes.size());
        List<PostLike> batch = likes.subList(i, end);
        postLikeMapper.batchInsert(batch);
    }
}
```

### 问题 2：事务日志增长过快

**问题描述：**
- 大批量插入导致事务日志快速增长
- 可能影响数据库性能
- 可能导致磁盘空间不足

**解决方案：**
1. **分批提交事务**
```java
public void batchInsertWithCommit(List<PostLike> likes) {
    int batchSize = 1000;
    for (int i = 0; i < likes.size(); i += batchSize) {
        int end = Math.min(i + batchSize, likes.size());
        List<PostLike> batch = likes.subList(i, end);
        
        // 每批单独事务
        transactionTemplate.execute(status -> {
            postLikeMapper.batchInsert(batch);
            return null;
        });
    }
}
```

2. **使用批量加载工具**
   - MySQL: `LOAD DATA INFILE`
   - PostgreSQL: `COPY`
   - 性能比 INSERT 快 10-100 倍

### 问题 3：重复数据插入

**问题描述：**
- 批量插入时可能包含重复数据
- 导致唯一约束冲突
- 整个批量操作失败

**解决方案：**
1. **插入前检查**
```java
// 先批量查询已存在的记录
List<PostLike> existing = postLikeMapper.batchExists(likes);
Set<String> existingSet = existing.stream()
    .map(l -> l.getPostId() + ":" + l.getUserId())
    .collect(Collectors.toSet());

// 过滤重复数据
List<PostLike> toInsert = likes.stream()
    .filter(l -> !existingSet.contains(l.getPostId() + ":" + l.getUserId()))
    .collect(Collectors.toList());
```

2. **使用 INSERT IGNORE**
```sql
INSERT IGNORE INTO post_like (post_id, user_id) VALUES
(1, 100), (2, 101), ...
```

3. **使用 ON DUPLICATE KEY UPDATE**
```sql
INSERT INTO post_like (post_id, user_id) VALUES
(1, 100), (2, 101), ...
ON DUPLICATE KEY UPDATE post_id = VALUES(post_id)
```

### 问题 4：内存溢出

**问题描述：**
- 一次性加载大量数据到内存
- 可能导致 OOM（Out of Memory）

**解决方案：**
1. **流式处理**
```java
// 使用流式处理，不一次性加载所有数据
public void batchInsertStream(Stream<PostLike> likeStream) {
    List<PostLike> batch = new ArrayList<>();
    int batchSize = 1000;
    
    likeStream.forEach(like -> {
        batch.add(like);
        if (batch.size() >= batchSize) {
            postLikeMapper.batchInsert(new ArrayList<>(batch));
            batch.clear();
        }
    });
    
    // 处理剩余数据
    if (!batch.isEmpty()) {
        postLikeMapper.batchInsert(batch);
    }
}
```

2. **分批读取和处理**
```java
// 从数据库分批读取，处理后再批量插入
public void processAndInsert() {
    int pageSize = 1000;
    int offset = 0;
    
    while (true) {
        List<SourceData> batch = sourceMapper.selectBatch(offset, pageSize);
        if (batch.isEmpty()) break;
        
        List<TargetData> transformed = transform(batch);
        targetMapper.batchInsert(transformed);
        
        offset += pageSize;
    }
}
```

### 问题 5：性能监控和优化

**问题描述：**
- 批量插入性能难以监控
- 不知道哪些批量操作慢

**解决方案：**
1. **添加性能监控**
```java
public void batchInsert(List<PostLike> likes) {
    long startTime = System.currentTimeMillis();
    try {
        postLikeMapper.batchInsert(likes);
        long duration = System.currentTimeMillis() - startTime;
        log.info("Batch insert completed: {} records, {}ms, {} records/ms", 
            likes.size(), duration, likes.size() * 1000.0 / duration);
    } catch (Exception e) {
        log.error("Batch insert failed: {} records", likes.size(), e);
        throw e;
    }
}
```

2. **数据库性能分析**
   - 使用 `EXPLAIN` 分析执行计划
   - 监控慢查询日志
   - 优化索引和表结构



## 最佳实践

1. **批量大小控制**
   - 小批量（< 100 条）：直接插入，性能差异不明显
   - 中批量（100-1000 条）：推荐批量插入
   - 大批量（> 1000 条）：分批处理，每批 500-2000 条

2. **事务管理**
   - 小批量：单事务处理
   - 大批量：分批提交，避免长事务
   - 关键数据：使用事务保证一致性

3. **错误处理**
   - 记录失败批次，支持重试
   - 使用死信队列处理失败数据
   - 提供数据校验机制

4. **性能优化**
   - 关闭自动提交，手动提交事务
   - 使用 JDBC 批量插入（性能最优）
   - 合理设置批量大小，平衡内存和性能

5. **监控告警**
   - 监控批量插入耗时
   - 监控批量大小分布
   - 监控失败率和重试次数



## 性能对比

| 操作方式 | 100条记录耗时 | 1000条记录耗时 | 10000条记录耗时 |
|---------|-------------|--------------|---------------|
| 逐条插入 | 500-1000ms | 5000-10000ms | 50000-100000ms |
| 批量插入 | 10-50ms | 50-200ms | 500-2000ms |
| **性能提升** | **10-50倍** | **25-50倍** | **25-100倍** |



## 总结

批量插入是数据库操作中最常用的批量处理方式，适用于：
- ✅ 日志记录
- ✅ 关系数据批量写入
- ✅ 数据导入/迁移
- ✅ 订单批量创建
- ✅ 任何需要批量写入数据的场景

**关键要点：**
1. 控制批量大小（500-2000 条/批）
2. 分批提交事务，避免长事务
3. 插入前检查重复数据
4. 添加性能监控和错误处理
5. 根据业务场景选择合适的批量大小

