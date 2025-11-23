# 数据库批量合并（Batch Merge）

## 使用情况

**常用程度：⭐⭐（较常用，Oracle特有）**

批量合并是 Oracle 数据库特有的批量处理方式，适用于"插入或更新"的场景，功能类似于 MySQL 的 `INSERT ... ON DUPLICATE KEY UPDATE`。

## 概述

批量合并是指使用 `MERGE` 语句批量插入或更新数据，根据条件判断是插入还是更新。这是 Oracle 数据库提供的标准 SQL 语法，功能强大，支持复杂的合并逻辑。

## 生产场景示例

### 1. 批量插入或更新（Batch Upsert）

**场景描述：**
- 批量插入数据，如果已存在则更新
- 数据同步、数据合并
- 避免先查询再插入/更新的两步操作

**实现示例：**
```java
// 批量插入或更新用户信息
public void batchMergeUsers(List<User> users) {
    if (users.isEmpty()) {
        return;
    }
    
    userMapper.batchMerge(users);
}

// MyBatis XML 实现
// <insert id="batchMerge">
//     MERGE INTO user_base u
//     USING (
//         <foreach collection="list" item="item" separator="UNION ALL">
//             SELECT #{item.id} AS id, #{item.username} AS username, #{item.email} AS email FROM DUAL
//         </foreach>
//     ) t ON (u.id = t.id)
//     WHEN MATCHED THEN UPDATE SET
//         username = t.username,
//         email = t.email,
//         update_time = SYSDATE
//     WHEN NOT MATCHED THEN INSERT (id, username, email, created_at)
//         VALUES (t.id, t.username, t.email, SYSDATE)
// </insert>
```

**业界案例：**
- **用户系统**：批量同步用户信息，存在则更新
- **商品系统**：批量同步商品信息，存在则更新价格、库存
- **订单系统**：批量同步订单状态，存在则更新状态

### 2. 数据仓库 ETL（Data Warehouse ETL）

**场景描述：**
- 从源系统抽取数据到数据仓库
- 需要处理新增、更新、删除等多种情况
- 保证数据一致性

**实现示例：**
```java
// 批量合并订单数据到数据仓库
public void batchMergeOrdersToWarehouse(List<Order> orders) {
    if (orders.isEmpty()) {
        return;
    }
    
    orderWarehouseMapper.batchMerge(orders);
}

// 使用示例：定时任务 ETL
@Scheduled(fixedDelay = 3600000) // 每小时执行一次
public void etlOrdersToWarehouse() {
    // 1. 从源系统获取订单数据
    List<Order> orders = sourceOrderService.fetchOrders();
    
    // 2. 批量合并到数据仓库
    batchMergeOrdersToWarehouse(orders);
}
```

**业界案例：**
- **数据仓库**：批量合并业务数据到数据仓库
- **BI系统**：批量合并数据到BI系统
- **分析系统**：批量合并数据到分析系统

### 3. 数据同步（Data Sync）

**场景描述：**
- 从外部系统同步数据
- 存在则更新，不存在则插入
- 保证数据一致性

**实现示例：**
```java
// 批量同步客户数据
public void batchSyncCustomers(List<Customer> customers) {
    if (customers.isEmpty()) {
        return;
    }
    
    customerMapper.batchMerge(customers);
}

// 使用示例：定时任务同步客户数据
@Scheduled(fixedDelay = 300000) // 每5分钟执行一次
public void syncCustomersFromExternalSystem() {
    // 1. 从外部系统获取客户数据
    List<Customer> customers = externalCustomerService.fetchCustomers();
    
    // 2. 批量合并
    batchSyncCustomers(customers);
}
```

**业界案例：**
- **CRM系统**：从第三方系统同步客户数据
- **ERP系统**：从外部系统同步业务数据
- **支付系统**：从支付网关同步交易数据

### 4. 增量更新（Incremental Update）

**场景描述：**
- 只更新变更的数据
- 提高更新效率
- 减少数据库压力

**实现示例：**
```java
// 增量更新用户数据
public void incrementalMergeUsers(List<User> users) {
    if (users.isEmpty()) {
        return;
    }
    
    userMapper.batchMerge(users);
}

// 使用示例：定时任务增量更新
@Scheduled(fixedDelay = 300000) // 每5分钟执行一次
public void incrementalUpdateUsers() {
    // 1. 查询上次更新后变更的用户
    Long lastUpdateTime = syncTimeService.getLastUpdateTime("user_sync");
    List<User> updatedUsers = userMapper.selectUpdatedAfter(lastUpdateTime);
    
    // 2. 批量合并
    incrementalMergeUsers(updatedUsers);
    
    // 3. 更新同步时间
    syncTimeService.updateSyncTime("user_sync", System.currentTimeMillis());
}
```

**业界案例：**
- **数据同步**：增量同步业务数据
- **数据仓库**：增量更新数据仓库
- **搜索引擎**：增量更新搜索索引

## Oracle 实现

### MERGE 语法

```sql
MERGE INTO target_table t
USING source_table s
ON (t.key = s.key)
WHEN MATCHED THEN
    UPDATE SET t.column1 = s.column1, t.column2 = s.column2
WHEN NOT MATCHED THEN
    INSERT (key, column1, column2) VALUES (s.key, s.column1, s.column2);
```

### 基本用法

```sql
-- 批量合并用户数据
MERGE INTO user_base u
USING (
    SELECT 1 AS id, 'user1' AS username, 'user1@example.com' AS email FROM DUAL
    UNION ALL
    SELECT 2 AS id, 'user2' AS username, 'user2@example.com' AS email FROM DUAL
) t ON (u.id = t.id)
WHEN MATCHED THEN UPDATE SET
    username = t.username,
    email = t.email,
    update_time = SYSDATE
WHEN NOT MATCHED THEN INSERT (id, username, email, created_at)
    VALUES (t.id, t.username, t.email, SYSDATE);
```

### MyBatis XML 实现

```xml
<!-- 批量合并用户数据 -->
<insert id="batchMerge">
    MERGE INTO user_base u
    USING (
        <foreach collection="list" item="item" separator="UNION ALL">
            SELECT 
                #{item.id} AS id,
                #{item.username} AS username,
                #{item.email} AS email
            FROM DUAL
        </foreach>
    ) t ON (u.id = t.id)
    WHEN MATCHED THEN UPDATE SET
        username = t.username,
        email = t.email,
        update_time = SYSDATE
    WHEN NOT MATCHED THEN INSERT (id, username, email, created_at)
        VALUES (t.id, t.username, t.email, SYSDATE)
</insert>

<!-- 批量合并多个字段 -->
<insert id="batchMergeComplex">
    MERGE INTO user_detail u
    USING (
        <foreach collection="list" item="item" separator="UNION ALL">
            SELECT 
                #{item.userId} AS user_id,
                #{item.nickname} AS nickname,
                #{item.avatar} AS avatar,
                #{item.bio} AS bio
            FROM DUAL
        </foreach>
    ) t ON (u.user_id = t.user_id)
    WHEN MATCHED THEN UPDATE SET
        nickname = t.nickname,
        avatar = t.avatar,
        bio = t.bio,
        update_time = SYSDATE
    WHEN NOT MATCHED THEN INSERT (user_id, nickname, avatar, bio, created_at)
        VALUES (t.user_id, t.nickname, t.avatar, t.bio, SYSDATE)
</insert>
```

### Java 实现

```java
// 批量合并用户数据
public void batchMergeUsers(List<User> users) {
    if (users.isEmpty()) {
        return;
    }
    
    userMapper.batchMerge(users);
}

// 分批合并，避免 SQL 过长
private static final int BATCH_SIZE = 1000;

public void batchMergeUsers(List<User> users) {
    for (int i = 0; i < users.size(); i += BATCH_SIZE) {
        int end = Math.min(i + BATCH_SIZE, users.size());
        List<User> batch = users.subList(i, end);
        userMapper.batchMerge(batch);
    }
}
```

## 优点

1. **功能强大**
   - 支持复杂的合并逻辑
   - 可以同时处理插入和更新
   - 支持条件判断

2. **性能优秀**
   - 一次 SQL 完成插入或更新
   - 比先查询再插入/更新快 2-5 倍
   - 数据库内部优化

3. **原子性保证**
   - 整个操作在一个事务中
   - 保证数据一致性

4. **标准 SQL**
   - Oracle 标准 SQL 语法
   - 功能强大，易于理解

## 缺点

1. **数据库特定**
   - Oracle 特有语法，不通用
   - MySQL 使用 `INSERT ... ON DUPLICATE KEY UPDATE`
   - PostgreSQL 使用 `ON CONFLICT DO UPDATE`

2. **SQL 复杂**
   - MERGE 语句相对复杂
   - 需要理解 USING、ON、WHEN MATCHED 等语法

3. **批量大小限制**
   - UNION ALL 子句有长度限制
   - 大批量数据需要分批处理

## 可能存在的问题及解决方案

### 问题 1：数据库兼容性

**问题描述：**
- MERGE 是 Oracle 特有语法
- 其他数据库不支持
- 需要为不同数据库提供不同实现

**解决方案：**
1. **使用数据库方言**
```java
// 根据数据库类型选择不同的 SQL
public void batchMergeUsers(List<User> users) {
    String dbType = dataSource.getDatabaseType();
    
    if ("Oracle".equals(dbType)) {
        userMapper.batchMergeOracle(users);
    } else if ("MySQL".equals(dbType)) {
        userMapper.batchInsertOrUpdateMySQL(users);
    } else if ("PostgreSQL".equals(dbType)) {
        userMapper.batchInsertOrUpdatePostgreSQL(users);
    }
}
```

2. **MySQL 实现**
```xml
<!-- MySQL: INSERT ... ON DUPLICATE KEY UPDATE -->
<insert id="batchInsertOrUpdateMySQL">
    INSERT INTO user_base (id, username, email) VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.id}, #{item.username}, #{item.email})
    </foreach>
    ON DUPLICATE KEY UPDATE
        username = VALUES(username),
        email = VALUES(email)
</insert>
```

3. **PostgreSQL 实现**
```xml
<!-- PostgreSQL: ON CONFLICT DO UPDATE -->
<insert id="batchInsertOrUpdatePostgreSQL">
    INSERT INTO user_base (id, username, email) VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.id}, #{item.username}, #{item.email})
    </foreach>
    ON CONFLICT (id) DO UPDATE SET
        username = EXCLUDED.username,
        email = EXCLUDED.email
</insert>
```

### 问题 2：SQL 长度限制

**问题描述：**
- UNION ALL 子句有长度限制
- 大批量数据可能导致 SQL 过长
- 可能超过数据库的 SQL 长度限制

**解决方案：**
```java
// 分批合并，控制每批大小
private static final int BATCH_SIZE = 1000;

public void batchMergeUsers(List<User> users) {
    for (int i = 0; i < users.size(); i += BATCH_SIZE) {
        int end = Math.min(i + BATCH_SIZE, users.size());
        List<User> batch = users.subList(i, end);
        userMapper.batchMerge(batch);
    }
}
```

### 问题 3：性能优化

**问题描述：**
- 大批量合并可能性能较差
- 需要优化合并逻辑

**解决方案：**
1. **使用临时表**
```sql
-- 使用临时表提高性能
CREATE GLOBAL TEMPORARY TABLE temp_users (
    id NUMBER,
    username VARCHAR2(50),
    email VARCHAR2(100)
) ON COMMIT DELETE ROWS;

-- 批量插入到临时表
INSERT INTO temp_users VALUES (1, 'user1', 'user1@example.com');
INSERT INTO temp_users VALUES (2, 'user2', 'user2@example.com');

-- 使用临时表合并
MERGE INTO user_base u
USING temp_users t ON (u.id = t.id)
WHEN MATCHED THEN UPDATE SET
    username = t.username,
    email = t.email
WHEN NOT MATCHED THEN INSERT (id, username, email)
    VALUES (t.id, t.username, t.email);
```

2. **使用批量绑定**
```java
// 使用批量绑定提高性能
public void batchMergeUsersWithBinding(List<User> users) {
    String sql = "MERGE INTO user_base u " +
                 "USING (SELECT ? AS id, ? AS username, ? AS email FROM DUAL) t " +
                 "ON (u.id = t.id) " +
                 "WHEN MATCHED THEN UPDATE SET username = t.username, email = t.email " +
                 "WHEN NOT MATCHED THEN INSERT (id, username, email) VALUES (t.id, t.username, t.email)";
    
    try (Connection conn = dataSource.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        conn.setAutoCommit(false);
        
        for (User user : users) {
            pstmt.setLong(1, user.getId());
            pstmt.setString(2, user.getUsername());
            pstmt.setString(3, user.getEmail());
            pstmt.addBatch();
        }
        
        pstmt.executeBatch();
        conn.commit();
    }
}
```

## 最佳实践

1. **批量大小控制**
   - 小批量（< 100 条）：直接合并
   - 中批量（100-1000 条）：推荐批量合并
   - 大批量（> 1000 条）：分批合并，每批 500-1000 条

2. **数据库兼容性**
   - Oracle：使用 MERGE 语句
   - MySQL：使用 INSERT ... ON DUPLICATE KEY UPDATE
   - PostgreSQL：使用 ON CONFLICT DO UPDATE

3. **性能优化**
   - 使用临时表提高性能
   - 使用批量绑定
   - 合理使用索引

4. **错误处理**
   - 处理合并异常
   - 记录失败日志
   - 支持重试机制

## 性能对比

| 操作方式 | 100条记录耗时 | 1000条记录耗时 | 性能 |
|---------|-------------|--------------|------|
| 先查询再插入/更新 | 200-500ms | 2000-5000ms | 较差 |
| MERGE 语句 | 50-150ms | 500-1500ms | **优秀** |

## 与其他方案对比

### vs MySQL INSERT ... ON DUPLICATE KEY UPDATE

**MERGE：**
- Oracle 标准 SQL 语法
- 功能强大，支持复杂逻辑
- 语法相对复杂

**INSERT ... ON DUPLICATE KEY UPDATE：**
- MySQL 特有语法
- 语法简单，易于理解
- 功能相对简单

### vs PostgreSQL ON CONFLICT DO UPDATE

**MERGE：**
- Oracle 标准 SQL 语法
- 功能强大

**ON CONFLICT DO UPDATE：**
- PostgreSQL 特有语法
- 语法简单

## 总结

批量合并（MERGE）是 Oracle 数据库提供的标准 SQL 语法，适用于：
- ✅ 批量插入或更新
- ✅ 数据仓库 ETL
- ✅ 数据同步
- ✅ 增量更新
- ✅ 任何需要"插入或更新"语义的场景

**关键要点：**
1. Oracle 标准 SQL 语法，功能强大
2. 一次 SQL 完成插入或更新，性能优秀
3. 支持复杂的合并逻辑
4. 注意数据库兼容性（Oracle 特有）
5. 控制批量大小，避免 SQL 过长

**适用场景：**
- ✅ Oracle 数据库
- ✅ 需要"插入或更新"语义
- ✅ 批量处理场景
- ✅ 对性能要求高

**不适用场景：**
- ❌ 非 Oracle 数据库（需要使用其他语法）
- ❌ 只需要插入或只需要更新的场景
- ❌ 需要区分插入和更新的场景（可以使用其他方式）

