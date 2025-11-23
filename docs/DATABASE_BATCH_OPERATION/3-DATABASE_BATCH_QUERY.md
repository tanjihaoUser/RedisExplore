# 数据库批量查询（Batch Query）

## 使用情况

**常用程度：⭐⭐⭐⭐⭐（非常常用）**

批量查询是数据库操作中最常用的查询优化方式，几乎所有的批量数据获取场景都会用到。

## 概述

批量查询是指将多条查询语句合并为一次数据库操作，通过减少数据库交互次数来提高查询性能。相比逐条查询，批量查询可以提升 10-100 倍的性能，是解决 N+1 查询问题的标准方案。

## 生产场景示例

### 1. 批量存在性检查（Batch Existence Check）

**场景描述：**
- 批量写入前检查数据是否已存在
- 避免重复插入，保证数据唯一性
- 高频操作，性能要求高

**实现示例：**
```java
// 批量检查点赞关系是否存在
public List<PostLike> batchExists(List<PostLike> candidates) {
    if (candidates.isEmpty()) {
        return Collections.emptyList();
    }
    
    // 一次查询所有候选记录
    return postLikeMapper.batchExists(candidates);
}

// 使用示例
List<PostLike> toInsert = candidates.stream()
    .filter(candidate -> {
        // 批量查询已存在的记录
        List<PostLike> existing = batchExists(candidates);
        Set<String> existingSet = existing.stream()
            .map(e -> e.getPostId() + ":" + e.getUserId())
            .collect(Collectors.toSet());
        
        String key = candidate.getPostId() + ":" + candidate.getUserId();
        return !existingSet.contains(key);
    })
    .collect(Collectors.toList());
```

**业界案例：**
- **抖音**：批量检查视频点赞关系，避免重复点赞
- **微博**：批量检查用户关注关系，避免重复关注
- **电商系统**：批量检查商品库存，避免超卖

### 2. 批量获取用户信息（Batch User Info）

**场景描述：**
- 列表页需要显示多个用户信息
- 避免 N+1 查询问题
- 提高页面加载速度

**实现示例：**
```java
// 批量获取用户信息
public Map<Long, User> batchGetUsers(List<Long> userIds) {
    if (userIds.isEmpty()) {
        return Collections.emptyMap();
    }
    
    // 一次查询所有用户
    List<User> users = userMapper.selectByIds(userIds);
    
    // 转换为 Map，方便查找
    return users.stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));
}

// 使用示例：帖子列表页
public List<PostVO> getPostList() {
    List<Post> posts = postMapper.selectList();
    
    // 批量获取所有作者信息（避免 N+1 查询）
    List<Long> authorIds = posts.stream()
        .map(Post::getAuthorId)
        .distinct()
        .collect(Collectors.toList());
    Map<Long, User> userMap = batchGetUsers(authorIds);
    
    // 组装返回数据
    return posts.stream()
        .map(post -> {
            PostVO vo = new PostVO();
            vo.setPost(post);
            vo.setAuthor(userMap.get(post.getAuthorId()));
            return vo;
        })
        .collect(Collectors.toList());
}
```

**业界案例：**
- **知乎**：问题列表批量获取作者信息
- **GitHub**：仓库列表批量获取所有者信息
- **LinkedIn**：动态列表批量获取用户信息

### 3. 批量检查权限（Batch Permission Check）

**场景描述：**
- 批量检查用户对多个资源的权限
- 避免逐条查询，提高性能
- 常用于权限控制中间件

**实现示例：**
```java
// 批量检查用户对资源的权限
public Map<String, Boolean> batchCheckPermission(Long userId, List<String> resources) {
    if (resources.isEmpty()) {
        return Collections.emptyMap();
    }
    
    // 一次查询所有权限
    List<Permission> permissions = permissionMapper.batchCheck(userId, resources);
    
    // 转换为 Map
    Set<String> allowedResources = permissions.stream()
        .map(Permission::getResource)
        .collect(Collectors.toSet());
    
    return resources.stream()
        .collect(Collectors.toMap(
            Function.identity(),
            allowedResources::contains
        ));
}
```

**业界案例：**
- **AWS**：批量检查 IAM 权限
- **阿里云**：批量检查资源访问权限
- **企业内部系统**：批量检查用户操作权限

### 4. 批量获取统计数据（Batch Statistics）

**场景描述：**
- 批量获取多个实体的统计数据
- 避免多次查询，提高性能
- 常用于报表和数据分析

**实现示例：**
```java
// 批量获取帖子统计数据
public Map<Long, PostStatistics> batchGetPostStatistics(List<Long> postIds) {
    if (postIds.isEmpty()) {
        return Collections.emptyMap();
    }
    
    // 一次查询所有统计数据
    List<PostStatistics> stats = postMapper.batchSelectStatistics(postIds);
    
    return stats.stream()
        .collect(Collectors.toMap(
            PostStatistics::getPostId,
            Function.identity()
        ));
}

// 使用示例：帖子列表页显示点赞数、评论数
public List<PostVO> getPostListWithStats() {
    List<Post> posts = postMapper.selectList();
    List<Long> postIds = posts.stream()
        .map(Post::getId)
        .collect(Collectors.toList());
    
    // 批量获取统计数据
    Map<Long, PostStatistics> statsMap = batchGetPostStatistics(postIds);
    
    return posts.stream()
        .map(post -> {
            PostVO vo = new PostVO();
            vo.setPost(post);
            PostStatistics stats = statsMap.get(post.getId());
            vo.setLikeCount(stats != null ? stats.getLikeCount() : 0);
            vo.setCommentCount(stats != null ? stats.getCommentCount() : 0);
            return vo;
        })
        .collect(Collectors.toList());
}
```

**业界案例：**
- **微博**：批量获取微博转发数、评论数、点赞数
- **B站**：批量获取视频播放数、弹幕数、投币数
- **GitHub**：批量获取仓库 Star 数、Fork 数、Issue 数

### 5. 批量检查状态（Batch Status Check）

**场景描述：**
- 批量检查多个实体的状态
- 避免逐条查询，提高性能
- 常用于状态同步和校验

**实现示例：**
```java
// 批量检查订单状态
public Map<Long, OrderStatus> batchCheckOrderStatus(List<Long> orderIds) {
    if (orderIds.isEmpty()) {
        return Collections.emptyMap();
    }
    
    // 一次查询所有订单状态
    List<Order> orders = orderMapper.selectByIds(orderIds);
    
    return orders.stream()
        .collect(Collectors.toMap(
            Order::getId,
            Order::getStatus
        ));
}
```

**业界案例：**
- **电商系统**：批量检查订单支付状态
- **物流系统**：批量检查包裹配送状态
- **支付系统**：批量检查交易状态

## MyBatis 实现

### XML 配置方式

```xml
<!-- 批量查询存在性 -->
<select id="batchExists" resultType="com.wait.entity.domain.PostLike">
    SELECT post_id AS postId, user_id AS userId
    FROM post_like
    WHERE (post_id, user_id) IN
    <foreach collection="likes" item="item" open="(" separator="," close=")">
        (#{item.postId}, #{item.userId})
    </foreach>
</select>

<!-- 批量查询用户信息 -->
<select id="selectByIds" resultType="com.wait.entity.domain.User">
    SELECT id, username, email, phone, status
    FROM user_base
    WHERE id IN
    <foreach collection="ids" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
    AND status != 0
</select>

<!-- 批量查询统计数据 -->
<select id="batchSelectStatistics" resultType="com.wait.entity.vo.PostStatistics">
    SELECT 
        post_id AS postId,
        COUNT(DISTINCT like_id) AS likeCount,
        COUNT(DISTINCT comment_id) AS commentCount,
        COUNT(DISTINCT favorite_id) AS favoriteCount
    FROM post_statistics
    WHERE post_id IN
    <foreach collection="postIds" item="postId" open="(" separator="," close=")">
        #{postId}
    </foreach>
    GROUP BY post_id
</select>
```

### 注解方式

```java
@Select({
    "<script>",
    "SELECT post_id AS postId, user_id AS userId",
    "FROM post_like",
    "WHERE (post_id, user_id) IN",
    "<foreach collection='likes' item='item' open='(' separator=',' close=')'>",
    "(#{item.postId}, #{item.userId})",
    "</foreach>",
    "</script>"
})
List<PostLike> batchExists(@Param("likes") List<PostLike> likes);
```

## 优点

1. **性能优秀**
   - 减少数据库交互次数，从 N 次减少到 1 次
   - 减少网络往返开销
   - 数据库可以优化批量查询，性能提升 10-100 倍

2. **解决 N+1 查询问题**
   - 避免循环查询导致的性能问题
   - 是 ORM 框架推荐的最佳实践

3. **代码简洁**
   - 实现简单，易于维护
   - 支持各种 ORM 框架

4. **数据库友好**
   - 减少数据库连接开销
   - 减少查询解析和执行开销

## 缺点

1. **SQL 语句可能过长**
   - IN 子句包含大量值时，SQL 语句可能很长
   - 可能超过数据库的 SQL 长度限制

2. **内存消耗**
   - 需要一次性加载所有查询结果到内存
   - 查询结果集很大时可能导致内存溢出

3. **索引利用**
   - IN 子句查询时，需要合理使用索引
   - 大量值可能导致索引效率下降

4. **结果集大小限制**
   - 某些数据库对 IN 子句的值数量有限制
   - MySQL 建议不超过 1000 个值

## 可能存在的问题及解决方案

### 问题 1：IN 子句值数量限制

**问题描述：**
- MySQL 的 `max_allowed_packet` 限制
- Oracle 的 `IN` 子句限制（1000 个值）
- SQL Server 的参数化查询限制（2100 个参数）

**解决方案：**
```java
// 分批查询，避免 IN 子句过长
private static final int BATCH_SIZE = 1000;

public List<PostLike> batchExists(List<PostLike> candidates) {
    if (candidates.isEmpty()) {
        return Collections.emptyList();
    }
    
    List<PostLike> results = new ArrayList<>();
    
    // 分批查询
    for (int i = 0; i < candidates.size(); i += BATCH_SIZE) {
        int end = Math.min(i + BATCH_SIZE, candidates.size());
        List<PostLike> batch = candidates.subList(i, end);
        
        List<PostLike> batchResults = postLikeMapper.batchExists(batch);
        results.addAll(batchResults);
    }
    
    return results;
}
```

### 问题 2：内存溢出

**问题描述：**
- 批量查询结果集很大时，可能导致内存溢出
- 一次性加载所有数据到内存

**解决方案：**
1. **流式处理**
```java
// 使用流式查询，分批处理结果
public void batchProcess(Stream<Long> idStream) {
    List<Long> batch = new ArrayList<>();
    int batchSize = 1000;
    
    idStream.forEach(id -> {
        batch.add(id);
        if (batch.size() >= batchSize) {
            List<User> users = userMapper.selectByIds(new ArrayList<>(batch));
            processUsers(users);
            batch.clear();
        }
    });
    
    // 处理剩余数据
    if (!batch.isEmpty()) {
        List<User> users = userMapper.selectByIds(batch);
        processUsers(users);
    }
}
```

2. **分页查询**
```java
// 分页批量查询
public List<User> batchGetUsersPaged(List<Long> userIds) {
    List<User> results = new ArrayList<>();
    int pageSize = 1000;
    
    for (int i = 0; i < userIds.size(); i += pageSize) {
        int end = Math.min(i + pageSize, userIds.size());
        List<Long> page = userIds.subList(i, end);
        
        List<User> pageResults = userMapper.selectByIds(page);
        results.addAll(pageResults);
    }
    
    return results;
}
```

### 问题 3：索引效率问题

**问题描述：**
- IN 子句包含大量值时，索引效率可能下降
- 数据库可能选择全表扫描而不是索引扫描

**解决方案：**
1. **使用临时表**
```sql
-- 创建临时表
CREATE TEMPORARY TABLE temp_ids (id BIGINT PRIMARY KEY);

-- 批量插入 ID
INSERT INTO temp_ids VALUES (1), (2), (3), ...;

-- 使用 JOIN 查询
SELECT u.* FROM user_base u
INNER JOIN temp_ids t ON u.id = t.id;
```

2. **使用 EXISTS 子查询**
```sql
-- 对于大量值，使用 EXISTS 可能更高效
SELECT u.* FROM user_base u
WHERE EXISTS (
    SELECT 1 FROM temp_ids t WHERE t.id = u.id
);
```

### 问题 4：空结果集处理

**问题描述：**
- 批量查询时，如果输入列表为空，应该返回空结果
- 避免执行无效查询

**解决方案：**
```java
public List<PostLike> batchExists(List<PostLike> candidates) {
    // 空列表直接返回
    if (candidates == null || candidates.isEmpty()) {
        return Collections.emptyList();
    }
    
    // 去重，避免重复查询
    Set<String> uniqueKeys = candidates.stream()
        .map(l -> l.getPostId() + ":" + l.getUserId())
        .collect(Collectors.toSet());
    
    if (uniqueKeys.isEmpty()) {
        return Collections.emptyList();
    }
    
    // 执行批量查询
    return postLikeMapper.batchExists(candidates);
}
```

### 问题 5：结果顺序问题

**问题描述：**
- 批量查询结果顺序可能与输入顺序不一致
- 需要保持输入顺序时，需要额外处理

**解决方案：**
```java
// 保持输入顺序的批量查询
public List<User> batchGetUsersOrdered(List<Long> userIds) {
    if (userIds.isEmpty()) {
        return Collections.emptyList();
    }
    
    // 批量查询
    List<User> users = userMapper.selectByIds(userIds);
    
    // 转换为 Map，方便查找
    Map<Long, User> userMap = users.stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));
    
    // 按照输入顺序返回
    return userIds.stream()
        .map(userMap::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
}

// 或者使用 SQL 的 FIELD 函数保持顺序
// SELECT * FROM user_base WHERE id IN (1, 3, 2) ORDER BY FIELD(id, 1, 3, 2)
```

## 最佳实践

1. **批量大小控制**
   - 小批量（< 100 条）：直接查询，性能差异不明显
   - 中批量（100-1000 条）：推荐批量查询
   - 大批量（> 1000 条）：分批查询，每批 500-1000 条

2. **索引优化**
   - 确保查询字段有索引
   - 对于复合查询，使用复合索引
   - 定期分析查询执行计划

3. **结果缓存**
   - 对于热点数据，使用缓存减少数据库查询
   - 设置合理的缓存过期时间
   - 注意缓存一致性问题

4. **错误处理**
   - 处理空结果集
   - 处理查询异常
   - 提供降级方案

5. **性能监控**
   - 监控批量查询耗时
   - 监控批量大小分布
   - 监控数据库连接池使用情况

## 性能对比

| 操作方式 | 100条记录耗时 | 1000条记录耗时 | 10000条记录耗时 |
|---------|-------------|--------------|---------------|
| 逐条查询 | 500-2000ms | 5000-20000ms | 50000-200000ms |
| 批量查询 | 10-50ms | 50-200ms | 500-2000ms |
| **性能提升** | **10-40倍** | **25-100倍** | **25-100倍** |

## N+1 查询问题解决

### 问题示例

```java
// ❌ 错误：N+1 查询问题
public List<PostVO> getPostListBad() {
    List<Post> posts = postMapper.selectList();
    
    // 每个帖子都查询一次作者信息（N+1 查询）
    return posts.stream()
        .map(post -> {
            PostVO vo = new PostVO();
            vo.setPost(post);
            vo.setAuthor(userMapper.selectById(post.getAuthorId())); // N 次查询
            return vo;
        })
        .collect(Collectors.toList());
}
```

### 解决方案

```java
// ✅ 正确：批量查询
public List<PostVO> getPostListGood() {
    List<Post> posts = postMapper.selectList();
    
    // 批量获取所有作者信息（1 次查询）
    List<Long> authorIds = posts.stream()
        .map(Post::getAuthorId)
        .distinct()
        .collect(Collectors.toList());
    Map<Long, User> userMap = batchGetUsers(authorIds);
    
    // 组装返回数据
    return posts.stream()
        .map(post -> {
            PostVO vo = new PostVO();
            vo.setPost(post);
            vo.setAuthor(userMap.get(post.getAuthorId())); // 内存查找
            return vo;
        })
        .collect(Collectors.toList());
}
```

## 总结

批量查询是数据库操作中最常用的查询优化方式，适用于：
- ✅ 批量存在性检查
- ✅ 批量获取实体信息
- ✅ 批量权限检查
- ✅ 批量统计数据获取
- ✅ 解决 N+1 查询问题
- ✅ 任何需要批量获取数据的场景

**关键要点：**
1. 控制批量大小（500-1000 条/批）
2. 合理使用索引
3. 处理空结果集和异常情况
4. 对于大批量数据，考虑分批查询
5. 监控查询性能，及时优化

