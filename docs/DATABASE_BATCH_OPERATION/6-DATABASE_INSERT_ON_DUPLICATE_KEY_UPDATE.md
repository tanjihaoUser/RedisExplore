# INSERT ... ON DUPLICATE KEY UPDATE

## 使用情况

**常用程度：⭐⭐⭐（较常用）**

INSERT ... ON DUPLICATE KEY UPDATE 是 MySQL 特有的批量处理方式，适用于"插入或更新"的场景。

## 概述

INSERT ... ON DUPLICATE KEY UPDATE 是 MySQL 提供的语法，可以在插入数据时，如果遇到唯一键冲突，则执行更新操作。这种语法可以实现"插入或更新"（Upsert）的语义，非常适合批量处理场景。

## 生产场景示例

### 1. 批量插入或更新（Batch Upsert）

**场景描述：**
- 批量插入数据，如果已存在则更新
- 数据同步、数据合并
- 避免先查询再插入/更新的两步操作

**实现示例：**
```java
// 批量插入或更新用户信息
public void batchUpsertUsers(List<User> users) {
    if (users.isEmpty()) {
        return;
    }
    
    userMapper.batchInsertOrUpdate(users);
}

// MyBatis XML 实现
// <insert id="batchInsertOrUpdate">
//     INSERT INTO user_base (id, username, email, update_time)
//     VALUES
//     <foreach collection="list" item="item" separator=",">
//         (#{item.id}, #{item.username}, #{item.email}, NOW())
//     </foreach>
//     ON DUPLICATE KEY UPDATE
//         username = VALUES(username),
//         email = VALUES(email),
//         update_time = NOW()
// </insert>
```

**业界案例：**
- **用户系统**：批量同步用户信息，存在则更新
- **商品系统**：批量同步商品信息，存在则更新价格、库存
- **订单系统**：批量同步订单状态，存在则更新状态

### 2. 计数器批量更新（Counter Batch Update）

**场景描述：**
- 批量更新计数器（点赞数、评论数等）
- 如果不存在则插入初始值，存在则累加

**实现示例：**
```java
// 批量更新帖子点赞数
public void batchUpdateLikeCount(Map<Long, Integer> postIdToCount) {
    List<PostCounter> counters = postIdToCount.entrySet().stream()
        .map(entry -> PostCounter.builder()
            .postId(entry.getKey())
            .likeCount(entry.getValue())
            .build())
        .collect(Collectors.toList());
    
    postMapper.batchUpsertLikeCount(counters);
}

// MyBatis XML 实现
// <insert id="batchUpsertLikeCount">
//     INSERT INTO post_counter (post_id, like_count)
//     VALUES
//     <foreach collection="list" item="item" separator=",">
//         (#{item.postId}, #{item.likeCount})
//     </foreach>
//     ON DUPLICATE KEY UPDATE
//         like_count = like_count + VALUES(like_count)
// </insert>
```

**业界案例：**
- **微博**：批量更新转发数、评论数、点赞数
- **B站**：批量更新播放数、弹幕数、投币数
- **GitHub**：批量更新 Star 数、Fork 数

### 3. 数据同步（Data Sync）

**场景描述：**
- 从其他数据源同步数据
- 存在则更新，不存在则插入
- 保证数据一致性

**实现示例：**
```java
// 批量同步订单数据
public void batchSyncOrders(List<Order> orders) {
    if (orders.isEmpty()) {
        return;
    }
    
    orderMapper.batchInsertOrUpdate(orders);
}

// 使用示例：定时任务同步订单数据
@Scheduled(fixedDelay = 300000) // 每5分钟执行一次
public void syncOrdersFromExternalSystem() {
    // 1. 从外部系统获取订单数据
    List<Order> orders = externalOrderService.fetchOrders();
    
    // 2. 批量插入或更新
    batchSyncOrders(orders);
}
```

**业界案例：**
- **电商系统**：从第三方系统同步订单数据
- **支付系统**：从支付网关同步交易数据
- **物流系统**：从物流公司同步物流信息

### 4. 配置信息批量更新（Config Batch Update）

**场景描述：**
- 批量更新系统配置信息
- 存在则更新，不存在则插入默认值

**实现示例：**
```java
// 批量更新系统配置
public void batchUpsertConfigs(Map<String, String> configs) {
    List<SystemConfig> configList = configs.entrySet().stream()
        .map(entry -> SystemConfig.builder()
            .key(entry.getKey())
            .value(entry.getValue())
            .build())
        .collect(Collectors.toList());
    
    configMapper.batchInsertOrUpdate(configList);
}
```

**业界案例：**
- **配置中心**：批量更新系统配置
- **参数管理**：批量更新业务参数
- **特性开关**：批量更新功能开关

## MyBatis 实现

### XML 配置方式

```xml
<!-- 批量插入或更新 -->
<insert id="batchInsertOrUpdate">
    INSERT INTO user_base (id, username, email, phone, update_time)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.id}, #{item.username}, #{item.email}, #{item.phone}, NOW())
    </foreach>
    ON DUPLICATE KEY UPDATE
        username = VALUES(username),
        email = VALUES(email),
        phone = VALUES(phone),
        update_time = NOW()
</insert>

<!-- 批量更新计数器（累加） -->
<insert id="batchUpsertLikeCount">
    INSERT INTO post_counter (post_id, like_count)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.postId}, #{item.likeCount})
    </foreach>
    ON DUPLICATE KEY UPDATE
        like_count = like_count + VALUES(like_count),
        update_time = NOW()
</insert>

<!-- 批量更新多个字段 -->
<insert id="batchInsertOrUpdateComplex">
    INSERT INTO user_detail (user_id, nickname, avatar, bio, update_time)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.userId}, #{item.nickname}, #{item.avatar}, #{item.bio}, NOW())
    </foreach>
    ON DUPLICATE KEY UPDATE
        nickname = VALUES(nickname),
        avatar = VALUES(avatar),
        bio = VALUES(bio),
        update_time = NOW()
</insert>
```

### 注解方式

```java
@Insert({
    "<script>",
    "INSERT INTO user_base (id, username, email) VALUES",
    "<foreach collection='list' item='item' separator=','>",
    "(#{item.id}, #{item.username}, #{item.email})",
    "</foreach>",
    "ON DUPLICATE KEY UPDATE",
    "username = VALUES(username),",
    "email = VALUES(email)",
    "</script>"
})
int batchInsertOrUpdate(@Param("list") List<User> users);
```

## 优点

1. **性能优秀**
   - 一次 SQL 完成插入或更新，减少数据库交互
   - 比先查询再插入/更新快 2-5 倍

2. **原子性保证**
   - 整个操作在一个事务中
   - 保证数据一致性

3. **代码简洁**
   - 不需要先查询再判断插入或更新
   - SQL 语句简洁明了

4. **避免竞态条件**
   - 原子操作，避免并发问题
   - 不需要额外的锁机制

## 缺点

1. **数据库特定**
   - MySQL 特有语法，不通用
   - PostgreSQL 使用 `ON CONFLICT DO UPDATE`
   - Oracle 使用 `MERGE` 语句

2. **无法区分插入和更新**
   - 无法知道哪些记录是新插入的，哪些是更新的
   - 如果需要统计，需要额外处理

3. **VALUES() 函数限制**
   - MySQL 8.0.19 之前，VALUES() 函数在某些场景下可能有问题
   - 新版本推荐使用别名方式

## 可能存在的问题及解决方案

### 问题 1：数据库兼容性

**问题描述：**
- MySQL 特有语法，其他数据库不支持
- 需要为不同数据库提供不同实现

**解决方案：**
1. **使用数据库方言**
```java
// 根据数据库类型选择不同的 SQL
public void batchInsertOrUpdate(List<User> users) {
    String dbType = dataSource.getDatabaseType();
    
    if ("MySQL".equals(dbType)) {
        userMapper.batchInsertOrUpdateMySQL(users);
    } else if ("PostgreSQL".equals(dbType)) {
        userMapper.batchInsertOrUpdatePostgreSQL(users);
    } else if ("Oracle".equals(dbType)) {
        userMapper.batchInsertOrUpdateOracle(users);
    }
}
```

2. **PostgreSQL 实现**
```xml
<!-- PostgreSQL: ON CONFLICT DO UPDATE -->
<insert id="batchInsertOrUpdatePostgreSQL">
    INSERT INTO user_base (id, username, email)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.id}, #{item.username}, #{item.email})
    </foreach>
    ON CONFLICT (id) DO UPDATE SET
        username = EXCLUDED.username,
        email = EXCLUDED.email
</insert>
```

3. **Oracle 实现**
```xml
<!-- Oracle: MERGE -->
<insert id="batchInsertOrUpdateOracle">
    MERGE INTO user_base u
    USING (
        <foreach collection="list" item="item" separator="UNION ALL">
            SELECT #{item.id} AS id, #{item.username} AS username, #{item.email} AS email FROM DUAL
        </foreach>
    ) t ON (u.id = t.id)
    WHEN MATCHED THEN UPDATE SET
        username = t.username,
        email = t.email
    WHEN NOT MATCHED THEN INSERT (id, username, email)
        VALUES (t.id, t.username, t.email)
</insert>
```

### 问题 2：VALUES() 函数问题

**问题描述：**
- MySQL 8.0.19 之前，VALUES() 函数在某些场景下可能有问题
- 新版本推荐使用别名方式

**解决方案：**
```xml
<!-- 旧方式（MySQL 8.0.19 之前） -->
<insert id="batchInsertOrUpdateOld">
    INSERT INTO user_base (id, username, email)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.id}, #{item.username}, #{item.email})
    </foreach>
    ON DUPLICATE KEY UPDATE
        username = VALUES(username),
        email = VALUES(email)
</insert>

<!-- 新方式（MySQL 8.0.19+，推荐） -->
<insert id="batchInsertOrUpdateNew">
    INSERT INTO user_base (id, username, email)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.id}, #{item.username}, #{item.email})
    </foreach>
    AS new
    ON DUPLICATE KEY UPDATE
        username = new.username,
        email = new.email
</insert>
```

### 问题 3：无法区分插入和更新

**问题描述：**
- 无法知道哪些记录是新插入的，哪些是更新的
- 如果需要统计，需要额外处理

**解决方案：**
1. **使用 ROW_COUNT()**
```java
// 执行后检查影响行数
public BatchUpsertResult batchInsertOrUpdate(List<User> users) {
    int affectedRows = userMapper.batchInsertOrUpdate(users);
    
    // affectedRows = 插入行数 + 更新行数 * 2
    // MySQL 返回：插入返回 1，更新返回 2
    // 但批量操作时，返回值可能不准确
    
    return BatchUpsertResult.builder()
        .totalRows(users.size())
        .affectedRows(affectedRows)
        .build();
}
```

2. **先查询再统计**
```java
// 先查询已存在的记录
public BatchUpsertResult batchInsertOrUpdateWithStats(List<User> users) {
    List<Long> existingIds = userMapper.selectExistingIds(
        users.stream().map(User::getId).collect(Collectors.toList())
    );
    Set<Long> existingSet = new HashSet<>(existingIds);
    
    // 执行批量插入或更新
    userMapper.batchInsertOrUpdate(users);
    
    // 统计插入和更新数量
    long insertCount = users.stream()
        .filter(user -> !existingSet.contains(user.getId()))
        .count();
    long updateCount = users.size() - insertCount;
    
    return BatchUpsertResult.builder()
        .insertCount(insertCount)
        .updateCount(updateCount)
        .build();
}
```

### 问题 4：唯一键冲突处理

**问题描述：**
- 需要确保表有唯一键或主键
- 多个唯一键时，需要明确使用哪个

**解决方案：**
```sql
-- 确保表有唯一键
CREATE TABLE user_base (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) UNIQUE,
    email VARCHAR(100) UNIQUE
);

-- 使用主键作为冲突检测
INSERT INTO user_base (id, username, email)
VALUES (1, 'user1', 'user1@example.com')
ON DUPLICATE KEY UPDATE
    username = VALUES(username),
    email = VALUES(email);

-- 如果需要使用其他唯一键，需要明确指定
-- MySQL 不支持，但可以通过应用层处理
```

### 问题 5：批量大小限制

**问题描述：**
- SQL 语句可能过长
- 可能超过 `max_allowed_packet` 限制

**解决方案：**
```java
// 分批处理，控制每批大小
private static final int BATCH_SIZE = 1000;

public void batchInsertOrUpdate(List<User> users) {
    for (int i = 0; i < users.size(); i += BATCH_SIZE) {
        int end = Math.min(i + BATCH_SIZE, users.size());
        List<User> batch = users.subList(i, end);
        userMapper.batchInsertOrUpdate(batch);
    }
}
```

## 最佳实践

1. **唯一键设计**
   - 确保表有主键或唯一键
   - 明确使用哪个唯一键作为冲突检测

2. **批量大小控制**
   - 小批量（< 100 条）：直接使用
   - 中批量（100-1000 条）：推荐使用
   - 大批量（> 1000 条）：分批处理，每批 500-1000 条

3. **字段更新策略**
   - 全量更新：更新所有字段
   - 部分更新：只更新指定字段
   - 累加更新：数值字段累加

4. **数据库兼容性**
   - MySQL：使用 `ON DUPLICATE KEY UPDATE`
   - PostgreSQL：使用 `ON CONFLICT DO UPDATE`
   - Oracle：使用 `MERGE` 语句

5. **性能优化**
   - 使用新版本的别名方式（MySQL 8.0.19+）
   - 控制批量大小，避免 SQL 过长
   - 合理使用索引

## 性能对比

| 操作方式 | 100条记录耗时 | 1000条记录耗时 |
|---------|-------------|--------------|
| 先查询再插入/更新 | 200-500ms | 2000-5000ms |
| INSERT ... ON DUPLICATE KEY UPDATE | 50-100ms | 500-1000ms |
| **性能提升** | **2-5倍** | **2-5倍** |

## 与其他方案对比

### vs INSERT IGNORE

**INSERT IGNORE：**
- 遇到冲突时忽略，不更新
- 适合"插入或跳过"的场景

**INSERT ... ON DUPLICATE KEY UPDATE：**
- 遇到冲突时更新
- 适合"插入或更新"的场景

### vs REPLACE INTO

**REPLACE INTO：**
- 遇到冲突时删除旧记录再插入新记录
- 会触发 DELETE 触发器，可能影响自增ID

**INSERT ... ON DUPLICATE KEY UPDATE：**
- 遇到冲突时只更新，不删除
- 不会触发 DELETE 触发器，不影响自增ID
- **推荐使用**

## 总结

INSERT ... ON DUPLICATE KEY UPDATE 是 MySQL 提供的批量处理方式，适用于：
- ✅ 批量插入或更新
- ✅ 计数器批量更新
- ✅ 数据同步
- ✅ 配置信息批量更新
- ✅ 任何需要"插入或更新"语义的场景

**关键要点：**
1. 确保表有唯一键或主键
2. 控制批量大小（500-1000 条/批）
3. 注意数据库兼容性（MySQL 特有）
4. 使用新版本的别名方式（MySQL 8.0.19+）
5. 合理使用索引，提高性能

**适用场景：**
- ✅ MySQL 数据库
- ✅ 需要"插入或更新"语义
- ✅ 批量处理场景
- ✅ 对性能要求高

**不适用场景：**
- ❌ 非 MySQL 数据库（需要使用其他语法）
- ❌ 需要区分插入和更新的场景
- ❌ 需要复杂更新逻辑的场景

