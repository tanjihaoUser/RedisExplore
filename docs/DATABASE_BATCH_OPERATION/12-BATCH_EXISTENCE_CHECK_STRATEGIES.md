# 批量写入时存在性检查策略对比

## 概述

在批量写入关系数据（如点赞、收藏）时，通常需要检查数据是否已存在，避免重复插入。本文档对比了业界常见的几种方案，并说明各自的优缺点和使用场景。

## 方案对比

### 1. 逐条查询（原方案）❌

**实现方式：**
```java
for (PostLike like : candidates) {
    boolean exists = postLikeMapper.exists(like.getPostId(), like.getUserId());
    if (!exists) {
        toInsert.add(like);
    }
}
```

**SQL 执行：**
```sql
-- 假设有 100 条记录，需要执行 100 次查询
SELECT COUNT(*) > 0 FROM post_like WHERE post_id = ? AND user_id = ?;  -- 第 1 次
SELECT COUNT(*) > 0 FROM post_like WHERE post_id = ? AND user_id = ?;  -- 第 2 次
-- ... 共 100 次
```

**优点：**
- 实现简单，逻辑清晰
- 适合单条或少量记录的场景

**缺点：**
- ❌ **N+1 查询问题**：100 条记录需要 100 次数据库交互
- ❌ **性能差**：数据库连接开销大，网络往返次数多
- ❌ **扩展性差**：随着批量大小增加，性能线性下降

**性能指标（假设 100 条记录）：**
- 数据库交互次数：**100 次**
- 网络往返次数：**100 次**
- 预计耗时：**100-500ms**（取决于网络延迟和数据库性能）

**适用场景：**
- ⚠️ **不推荐用于批量操作**
- 仅适用于单条或极少量（< 5 条）记录的场景

---

### 2. 批量查询（推荐方案）✅

**实现方式：**
```java
// 1. 批量查询已存在的记录
List<PostLike> existing = postLikeMapper.batchExists(candidates);

// 2. 构建已存在记录的 Set，用于快速查找
Set<String> existingSet = new HashSet<>();
for (PostLike like : existing) {
    existingSet.add(like.getPostId() + ":" + like.getUserId());
}

// 3. 过滤出需要插入的记录
List<PostLike> toInsert = new ArrayList<>();
for (PostLike candidate : candidates) {
    String key = candidate.getPostId() + ":" + candidate.getUserId();
    if (!existingSet.contains(key)) {
        toInsert.add(candidate);
    }
}
```

**SQL 执行：**
```sql
-- 只需执行 1 次查询
SELECT post_id, user_id 
FROM post_like 
WHERE (post_id, user_id) IN 
    ((1, 100), (2, 101), (3, 102), ..., (100, 199));
```

**优点：**
- ✅ **性能优秀**：100 条记录只需 1 次数据库交互
- ✅ **扩展性好**：批量大小增加时，性能提升明显
- ✅ **网络开销小**：减少网络往返次数
- ✅ **数据库压力小**：减少数据库连接和查询解析开销

**缺点：**
- 需要额外的内存存储已存在记录的 Set
- SQL 语句可能较长（大量记录时）

**性能指标（假设 100 条记录）：**
- 数据库交互次数：**1 次**
- 网络往返次数：**1 次**
- 预计耗时：**10-50ms**（比逐条查询快 10-50 倍）

**适用场景：**
- ✅ **推荐用于批量操作**
- 适合批量大小 > 10 条的场景
- 当前实现采用的方案

**注意事项：**
- MySQL 的 `IN` 子句有长度限制（通常 1000-2000 个值），超过时需要分批查询
- 建议批量大小控制在 500-1000 条以内

---

### 3. 忽略检查 + 数据库唯一约束

**实现方式：**
```java
// 直接批量插入，依赖数据库唯一约束处理重复
postLikeMapper.batchInsert(candidates);
```

**数据库表结构：**
```sql
CREATE TABLE post_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    UNIQUE KEY uk_post_user (post_id, user_id)  -- 唯一约束
);
```

**SQL 执行：**
```sql
-- 直接插入，数据库会检查唯一约束
INSERT INTO post_like (post_id, user_id) VALUES
    (1, 100), (2, 101), ..., (100, 199);
-- 如果存在重复，数据库会抛出异常或忽略（取决于 INSERT 语句）
```

**优点：**
- ✅ **性能最优**：无需查询，直接插入
- ✅ **实现最简单**：不需要额外的查询逻辑
- ✅ **数据一致性最强**：数据库层面保证唯一性

**缺点：**
- ❌ **需要处理异常**：重复插入时会抛出异常，需要捕获处理
- ❌ **错误信息不明确**：无法区分哪些记录已存在，哪些是新插入的
- ❌ **不适合部分成功场景**：如果批量插入中有部分重复，整个事务可能失败

**性能指标（假设 100 条记录，无重复）：**
- 数据库交互次数：**1 次**
- 网络往返次数：**1 次**
- 预计耗时：**5-20ms**（最快）

**适用场景：**
- ✅ 适合数据重复率极低的场景（< 1%）
- ✅ 适合可以接受部分失败重试的场景
- ⚠️ 需要配合异常处理和重试机制

**实现示例（带异常处理）：**
```java
try {
    postLikeMapper.batchInsert(candidates);
} catch (DuplicateKeyException e) {
    // 处理重复键异常，可能需要逐条插入或使用其他策略
    log.warn("Duplicate key detected, falling back to individual inserts");
    for (PostLike like : candidates) {
        try {
            postLikeMapper.insert(like);
        } catch (DuplicateKeyException ignored) {
            // 忽略已存在的记录
        }
    }
}
```

---

### 4. INSERT IGNORE / ON DUPLICATE KEY UPDATE（MySQL 特有）

**实现方式：**
```xml
<!-- INSERT IGNORE：忽略重复键错误 -->
<insert id="batchInsertIgnore">
    INSERT IGNORE INTO post_like (post_id, user_id) VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.postId}, #{item.userId})
    </foreach>
</insert>

<!-- ON DUPLICATE KEY UPDATE：重复时更新 -->
<insert id="batchInsertOrUpdate">
    INSERT INTO post_like (post_id, user_id) VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.postId}, #{item.userId})
    </foreach>
    ON DUPLICATE KEY UPDATE post_id = VALUES(post_id)
</insert>
```

**SQL 执行：**
```sql
-- INSERT IGNORE：重复的记录会被忽略，不报错
INSERT IGNORE INTO post_like (post_id, user_id) VALUES
    (1, 100), (2, 101), ..., (100, 199);

-- ON DUPLICATE KEY UPDATE：重复时执行更新操作
INSERT INTO post_like (post_id, user_id) VALUES
    (1, 100), (2, 101), ..., (100, 199)
ON DUPLICATE KEY UPDATE post_id = VALUES(post_id);
```

**优点：**
- ✅ **性能优秀**：只需 1 次数据库交互
- ✅ **无需额外查询**：数据库自动处理重复
- ✅ **原子性保证**：整个批量操作在一个事务中

**缺点：**
- ❌ **数据库特定**：MySQL 特有语法，不通用
- ❌ **无法区分新插入和已存在**：无法统计实际插入了多少条新记录
- ❌ **INSERT IGNORE 静默失败**：可能掩盖数据问题

**性能指标（假设 100 条记录）：**
- 数据库交互次数：**1 次**
- 网络往返次数：**1 次**
- 预计耗时：**5-20ms**（与方案 3 相当）

**适用场景：**
- ✅ 适合 MySQL 数据库
- ✅ 适合不需要区分新插入和已存在记录的场景
- ✅ 适合需要"插入或更新"语义的场景（使用 ON DUPLICATE KEY UPDATE）

---

## 性能对比总结

| 方案 | 数据库交互次数（100条） | 预计耗时 | 实现复杂度 | 推荐度 |
|------|----------------------|---------|-----------|--------|
| **逐条查询** | 100 次 | 100-500ms | ⭐ 简单 | ❌ 不推荐 |
| **批量查询** | 1 次 | 10-50ms | ⭐⭐ 中等 | ✅ **推荐** |
| **忽略检查+约束** | 1 次 | 5-20ms | ⭐ 简单 | ⚠️ 需异常处理 |
| **INSERT IGNORE** | 1 次 | 5-20ms | ⭐ 简单 | ⚠️ MySQL 特有 |

## 当前实现方案

当前实现采用 **批量查询方案**，原因如下：

1. ✅ **性能优秀**：比逐条查询快 10-50 倍
2. ✅ **数据库通用**：不依赖 MySQL 特有语法
3. ✅ **逻辑清晰**：可以明确区分新插入和已存在的记录
4. ✅ **易于监控**：可以统计实际插入的记录数
5. ✅ **错误处理简单**：不会因为重复数据导致整个批量操作失败

### 实现细节

```java
// 1. 批量查询已存在的记录（1 次数据库交互）
List<PostLike> existing = postLikeMapper.batchExists(candidates);

// 2. 构建 HashSet 用于快速查找（O(1) 时间复杂度）
Set<String> existingSet = new HashSet<>();
for (PostLike like : existing) {
    existingSet.add(like.getPostId() + ":" + like.getUserId());
}

// 3. 过滤出需要插入的记录（内存操作，性能高）
List<PostLike> toInsert = candidates.stream()
    .filter(like -> !existingSet.contains(like.getPostId() + ":" + like.getUserId()))
    .collect(Collectors.toList());
```

### SQL 实现

```xml
<select id="batchExists" resultType="com.wait.entity.domain.PostLike">
    SELECT post_id AS postId, user_id AS userId
    FROM post_like
    WHERE (post_id, user_id) IN
    <foreach collection="likes" item="item" open="(" separator="," close=")">
        (#{item.postId}, #{item.userId})
    </foreach>
</select>
```

## 最佳实践建议

### 1. 批量大小控制

- **小批量（< 10 条）**：可以使用逐条查询，性能差异不明显
- **中批量（10-100 条）**：推荐使用批量查询
- **大批量（> 100 条）**：必须使用批量查询，并考虑分批处理（避免 SQL 过长）

### 2. 数据库兼容性

- **MySQL**：可以使用 `INSERT IGNORE` 或 `ON DUPLICATE KEY UPDATE`
- **PostgreSQL**：可以使用 `ON CONFLICT DO NOTHING` 或 `ON CONFLICT DO UPDATE`
- **通用方案**：使用批量查询，兼容所有数据库

### 3. 异常处理

- **批量查询方案**：可以优雅处理部分记录已存在的情况
- **忽略检查方案**：需要捕获 `DuplicateKeyException` 并处理
- **INSERT IGNORE**：静默忽略重复，需要额外监控

### 4. 监控指标

建议监控以下指标：
- 批量查询耗时
- 实际插入记录数 vs 候选记录数（重复率）
- 批量大小分布
- 数据库连接池使用情况

## 总结

对于批量写入时的存在性检查，**批量查询方案**是最佳选择：

1. ✅ **性能优秀**：比逐条查询快 10-50 倍
2. ✅ **数据库通用**：不依赖特定数据库语法
3. ✅ **逻辑清晰**：可以明确区分新插入和已存在的记录
4. ✅ **易于维护**：代码逻辑清晰，易于理解和调试

**不推荐使用逐条查询**，除非批量大小极小（< 5 条）。

**可以考虑使用 INSERT IGNORE**，如果：
- 使用 MySQL 数据库
- 不需要区分新插入和已存在的记录
- 可以接受静默忽略重复数据

