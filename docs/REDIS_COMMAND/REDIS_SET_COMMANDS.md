# Redis Set 命令列表

本文档基于 `BoundUtil` 中的 Set 相关方法，总结 Redis Set 类型的所有命令、使用场景和示例代码。

Redis Set 是一个无序、不重复的字符串集合，非常适合用于社交媒体场景中的关注关系、点赞记录、收藏列表等功能。



## 1. 基础操作

### SADD - 添加成员

**Redis命令：** `SADD key member [member ...]`

**方法签名：**
```java
boundUtil.sAdd(key, value1, value2, ...);
```

**用途：** 向集合中添加一个或多个成员，如果成员已存在则忽略。

**参数说明：**
- `key`: 集合的键名
- `value1, value2, ...`: 要添加的一个或多个成员（可变参数）

**返回值：** 实际添加的成员数量（不包括已存在的成员）。

**使用场景：**

1. **添加关注关系**
   ```java
   // 用户1关注用户2
   Long added = boundUtil.sAdd("user:follow:1", 2L);
   // added = 1 表示成功添加，added = 0 表示已经关注过
   ```

2. **添加点赞记录**
   ```java
   // 用户1点赞帖子100
   Long added = boundUtil.sAdd("post:like:100", 1L);
   // 防止重复点赞
   if (added > 0) {
       // 更新点赞计数
       boundUtil.incr("post:like_count:100");
   }
   ```

3. **添加收藏记录**
   ```java
   // 用户1收藏帖子100
   boundUtil.sAdd("user:favorite:1", 100L);
   ```

4. **标记空用户集合（当前项目使用场景）**
   ```java
   // 标记用户没有帖子，避免频繁查询数据库
   boundUtil.sAdd("user:post:empty:set", userId);
   ```

5. **添加标签/话题**
   ```java
   // 帖子100的标签：科技、编程、Java
   boundUtil.sAdd("post:tags:100", "科技", "编程", "Java");
   ```

6. **添加黑名单**
   ```java
   // 用户1将用户2加入黑名单
   boundUtil.sAdd("user:blacklist:1", 2L);
   ```



### SREM - 删除成员

**Redis命令：** `SREM key member [member ...]`

**方法签名：**
```java
boundUtil.sRem(key, value1, value2, ...);
```

**用途：** 从集合中删除一个或多个成员。

**参数说明：**
- `key`: 集合的键名
- `value1, value2, ...`: 要删除的一个或多个成员（可变参数）

**返回值：** 实际删除的成员数量。

**使用场景：**

1. **取消关注**
   ```java
   // 用户1取消关注用户2
   Long removed = boundUtil.sRem("user:follow:1", 2L);
   if (removed > 0) {
       // 同时删除对方的粉丝记录
       boundUtil.sRem("user:follower:2", 1L);
   }
   ```

2. **取消点赞**
   ```java
   // 用户1取消点赞帖子100
   Long removed = boundUtil.sRem("post:like:100", 1L);
   if (removed > 0) {
       // 更新点赞计数
       boundUtil.incrBy("post:like_count:100", -1);
   }
   ```

3. **取消收藏**
   ```java
   // 用户1取消收藏帖子100
   boundUtil.sRem("user:favorite:1", 100L);
   ```

4. **从空用户集合移除（当前项目使用场景）**
   ```java
   // 用户发布新帖子后，从空用户Set中移除
   boundUtil.sRem("user:post:empty:set", userId);
   ```

5. **移除黑名单**
   ```java
   // 用户1将用户2移出黑名单
   boundUtil.sRem("user:blacklist:1", 2L);
   ```



### SMEMBERS - 获取所有成员

**Redis命令：** `SMEMBERS key`

**方法签名：**
```java
boundUtil.sMembers(key, Class<T> clazz);
```

**用途：** 返回集合中的所有成员。

**参数说明：**
- `key`: 集合的键名
- `clazz`: 返回成员类型（如 `Long.class`, `String.class`）

**返回值：** 集合中所有成员的 Set，如果 key 不存在返回空 Set。

**注意：** 对于大集合（成员数 > 1万），`SMEMBERS` 会阻塞 Redis，建议使用 `SSCAN` 进行遍历。

**使用场景：**

1. **获取关注列表**
   ```java
   // 获取用户1关注的所有用户ID
   Set<Long> following = boundUtil.sMembers("user:follow:1", Long.class);
   ```

2. **获取点赞用户列表**
   ```java
   // 获取点赞帖子100的所有用户ID
   Set<Long> likers = boundUtil.sMembers("post:like:100", Long.class);
   ```

3. **获取收藏列表**
   ```java
   // 获取用户1收藏的所有帖子ID
   Set<Long> favorites = boundUtil.sMembers("user:favorite:1", Long.class);
   ```

4. **获取标签列表**
   ```java
   // 获取帖子100的所有标签
   Set<String> tags = boundUtil.sMembers("post:tags:100", String.class);
   ```

5. **获取黑名单列表**
   ```java
   // 获取用户1的黑名单
   Set<Long> blacklist = boundUtil.sMembers("user:blacklist:1", Long.class);
   ```



### SISMEMBER - 判断成员是否存在

**Redis命令：** `SISMEMBER key member`

**方法签名：**
```java
boundUtil.sIsMember(key, value);
```

**用途：** 判断指定成员是否存在于集合中。

**参数说明：**
- `key`: 集合的键名
- `value`: 要检查的成员

**返回值：** `true` 表示成员存在，`false` 表示不存在或 key 不存在。

**使用场景：**

1. **检查是否已关注**
   ```java
   // 检查用户1是否关注了用户2
   Boolean isFollowing = boundUtil.sIsMember("user:follow:1", 2L);
   if (Boolean.TRUE.equals(isFollowing)) {
       // 已关注，显示"取消关注"按钮
   } else {
       // 未关注，显示"关注"按钮
   }
   ```

2. **检查是否已点赞**
   ```java
   // 检查用户1是否点赞了帖子100
   Boolean isLiked = boundUtil.sIsMember("post:like:100", 1L);
   // 前端根据返回值显示点赞状态（已点赞/未点赞）
   ```

3. **检查是否已收藏**
   ```java
   // 检查用户1是否收藏了帖子100
   Boolean isFavorited = boundUtil.sIsMember("user:favorite:1", 100L);
   ```

4. **检查是否在空用户集合中（当前项目使用场景）**
   ```java
   // 检查用户是否在空用户Set中，避免频繁查询数据库
   Boolean isInEmptySet = boundUtil.sIsMember("user:post:empty:set", userId);
   if (Boolean.TRUE.equals(isInEmptySet)) {
       // 用户确实没有帖子，直接返回空列表
       return Collections.emptyList();
   }
   ```

5. **检查是否在黑名单中**
   ```java
   // 检查用户2是否在用户1的黑名单中
   Boolean isBlacklisted = boundUtil.sIsMember("user:blacklist:1", 2L);
   if (Boolean.TRUE.equals(isBlacklisted)) {
       // 被拉黑，禁止查看内容或发送消息
   }
   ```



### SCARD - 获取集合大小

**Redis命令：** `SCARD key`

**方法签名：**
```java
boundUtil.sCard(key);
```

**用途：** 返回集合中的成员数量。

**参数说明：**
- `key`: 集合的键名

**返回值：** 集合中成员的数量，如果 key 不存在返回 0。

**使用场景：**

1. **统计关注数**
   ```java
   // 获取用户1的关注数
   Long followingCount = boundUtil.sCard("user:follow:1");
   ```

2. **统计粉丝数**
   ```java
   // 获取用户1的粉丝数
   Long followerCount = boundUtil.sCard("user:follower:1");
   ```

3. **统计点赞数**
   ```java
   // 获取帖子100的点赞数（从Set获取，更准确）
   Long likeCount = boundUtil.sCard("post:like:100");
   // 注意：如果同时维护计数，需要保持一致性
   ```

4. **统计收藏数**
   ```java
   // 获取用户1的收藏数
   Long favoriteCount = boundUtil.sCard("user:favorite:1");
   ```

5. **统计帖子标签数**
   ```java
   // 获取帖子100的标签数量
   Long tagCount = boundUtil.sCard("post:tags:100");
   ```



### SPOP - 随机弹出成员

**Redis命令：** `SPOP key [count]`

**方法签名：**
```java
boundUtil.sPop(key, Class<T> clazz);  // 弹出1个成员
```

**用途：** 随机弹出并返回集合中的一个或多个成员。

**参数说明：**
- `key`: 集合的键名
- `clazz`: 返回成员类型

**返回值：** 弹出的成员，如果集合为空返回 `null`。

**注意：** `SPOP` 会从集合中移除成员，适合需要随机选择且不再需要的场景。

**使用场景：**

1. **随机推荐用户**
   ```java
   // 从候选用户集合中随机选择一个推荐给当前用户
   Long recommendedUserId = boundUtil.sPop("user:candidate:recommend", Long.class);
   if (recommendedUserId != null) {
       // 推荐给用户
   }
   ```

2. **抽奖系统**
   ```java
   // 从参与者集合中随机抽取中奖者
   Long winner = boundUtil.sPop("lottery:participants", Long.class);
   ```

3. **随机分配任务**
   ```java
   // 从待处理任务集合中随机分配一个任务
   Long taskId = boundUtil.sPop("task:pending", Long.class);
   ```



## 2. 集合运算

### SDIFF - 差集

**Redis命令：** `SDIFF key [key ...]`（Redis 原生命令，BoundUtil 中可能需要通过原生 RedisTemplate 调用）

**用途：** 返回第一个集合与其他集合的差集（在第一个集合中但不在其他集合中的成员）。

**使用场景：**

1. **查找互不关注的用户**
   ```java
   // 用户1的关注列表 减去 用户1的粉丝列表 = 用户1单方面关注的人
   Set<Long> oneWayFollowing = redisTemplate.opsForSet().difference(
       "user:follow:1", "user:follower:1");
   ```

2. **查找新关注但未互关的用户**
   ```java
   // 用户1的关注列表 减去 用户2的关注列表 = 用户1关注但用户2未关注的人
   Set<Long> newFollows = redisTemplate.opsForSet().difference(
       "user:follow:1", "user:follow:2");
   ```

3. **推荐系统：找出用户未关注但朋友关注的**
   ```java
   // 朋友关注列表 减去 当前用户关注列表 = 推荐关注列表
   Set<Long> recommendations = redisTemplate.opsForSet().difference(
       "user:follow:friend", "user:follow:current");
   ```



### SINTER - 交集

**Redis命令：** `SINTER key [key ...]`（Redis 原生命令，BoundUtil 中可能需要通过原生 RedisTemplate 调用）

**用途：** 返回所有给定集合的交集（同时存在于所有集合中的成员）。

**使用场景：**

1. **查找共同关注（Mutual Follow）**
   ```java
   // 用户1和用户2的共同关注列表
   Set<Long> mutualFollowing = redisTemplate.opsForSet().intersect(
       "user:follow:1", "user:follow:2");
   ```

2. **查找共同粉丝**
   ```java
   // 用户1和用户2的共同粉丝
   Set<Long> mutualFollowers = redisTemplate.opsForSet().intersect(
       "user:follower:1", "user:follower:2");
   ```

3. **查找共同点赞的帖子**
   ```java
   // 用户1和用户2都点赞的帖子
   Set<Long> mutualLikes = redisTemplate.opsForSet().intersect(
       "user:like:1", "user:like:2");
   ```

4. **查找共同收藏**
   ```java
   // 用户1和用户2都收藏的帖子
   Set<Long> mutualFavorites = redisTemplate.opsForSet().intersect(
       "user:favorite:1", "user:favorite:2");
   ```

5. **标签匹配：查找包含多个标签的帖子**
   ```java
   // 包含"科技"和"编程"两个标签的帖子ID
   Set<Long> posts = redisTemplate.opsForSet().intersect(
       "tag:posts:科技", "tag:posts:编程");
   ```

6. **兴趣匹配：推荐共同兴趣的用户**
   ```java
   // 用户1和用户2的共同兴趣标签
   Set<String> commonInterests = redisTemplate.opsForSet().intersect(
       "user:interests:1", "user:interests:2");
   // 共同兴趣越多，推荐权重越高
   ```



### SUNION - 并集

**Redis命令：** `SUNION key [key ...]`（Redis 原生命令，BoundUtil 中可能需要通过原生 RedisTemplate 调用）

**用途：** 返回所有给定集合的并集（所有集合中的成员，去重后）。

**使用场景：**

1. **合并关注列表**
   ```java
   // 用户1和用户2的关注列表合并（去重）
   Set<Long> allFollowing = redisTemplate.opsForSet().union(
       "user:follow:1", "user:follow:2");
   ```

2. **合并标签**
   ```java
   // 帖子100的所有标签合并（如果有多个标签Set）
   Set<String> allTags = redisTemplate.opsForSet().union(
       "post:tags:100:category", "post:tags:100:topic");
   ```

3. **合并点赞列表**
   ```java
   // 多个帖子合并后的所有点赞用户
   Set<Long> allLikers = redisTemplate.opsForSet().union(
       "post:like:100", "post:like:101", "post:like:102");
   ```

4. **推荐系统：合并多个推荐源**
   ```java
   // 基于关注的推荐 并集 基于兴趣的推荐
   Set<Long> recommendations = redisTemplate.opsForSet().union(
       "recommend:follow:1", "recommend:interest:1");
   ```



### SDIFFSTORE - 差集并存储

**Redis命令：** `SDIFFSTORE destination key [key ...]`（Redis 原生命令）

**用途：** 计算差集并将结果存储到新的集合中。

**使用场景：**

```java
// 计算用户1和用户2的关注差集，存储到新集合
Long count = redisTemplate.opsForSet().differenceAndStore(
    "user:follow:1", "user:follow:2", "user:follow:diff:1:2");
```



### SINTERSTORE - 交集并存储

**Redis命令：** `SINTERSTORE destination key [key ...]`（Redis 原生命令）

**用途：** 计算交集并将结果存储到新的集合中。

**使用场景：**

```java
// 计算用户1和用户2的共同关注，存储到临时集合（可用于缓存）
Long count = redisTemplate.opsForSet().intersectAndStore(
    "user:follow:1", "user:follow:2", "mutual:follow:1:2");
// 设置过期时间，避免占用过多内存
redisTemplate.expire("mutual:follow:1:2", 1, TimeUnit.HOURS);
```



### SUNIONSTORE - 并集并存储

**Redis命令：** `SUNIONSTORE destination key [key ...]`（Redis 原生命令）

**用途：** 计算并集并将结果存储到新的集合中。

**使用场景：**

```java
// 合并多个用户的所有关注，存储到新集合
Long count = redisTemplate.opsForSet().unionAndStore(
    Arrays.asList("user:follow:1", "user:follow:2", "user:follow:3"),
    "group:follow:all");
```



## 3. 随机操作

### SRANDMEMBER - 随机获取成员

**Redis命令：** `SRANDMEMBER key [count]`（BoundUtil 中可能需要通过原生 RedisTemplate 调用）

**用途：** 随机返回集合中的一个或多个成员，**不会移除成员**（与 `SPOP` 的区别）。

**参数说明：**
- `count > 0`: 返回 count 个不重复的成员
- `count < 0`: 返回 |count| 个成员，可能包含重复
- `count = 0` 或不指定: 返回 1 个成员

**使用场景：**

1. **随机推荐帖子**
   ```java
   // 从用户收藏列表中随机推荐3个帖子
   Set<Long> recommendations = redisTemplate.opsForSet().randomMembers(
       "user:favorite:1", 3);
   ```

2. **随机展示标签**
   ```java
   // 随机展示5个热门标签
   List<String> randomTags = new ArrayList<>(
       redisTemplate.opsForSet().randomMembers("tags:hot", 5));
   ```

3. **随机用户推荐**
   ```java
   // 从用户池中随机推荐10个用户
   Set<Long> randomUsers = redisTemplate.opsForSet().randomMembers(
       "user:pool:active", 10);
   ```



## 4. 移动操作

### SMOVE - 移动成员

**Redis命令：** `SMOVE source destination member`（BoundUtil 中可能需要通过原生 RedisTemplate 调用）

**用途：** 将成员从源集合移动到目标集合。

**使用场景：**

1. **迁移关注关系**
   ```java
   // 将用户1从关注列表移动到特别关注列表
   Boolean moved = redisTemplate.opsForSet().move(
       "user:follow:1", "user:follow:special:1", 2L);
   ```

2. **从待审核列表移动到已审核列表**
   ```java
   // 帖子审核通过后，从待审核移动到已发布
   redisTemplate.opsForSet().move(
       "post:pending", "post:published", postId);
   ```



## 5. 扫描操作

### SSCAN - 增量遍历

**Redis命令：** `SSCAN key cursor [MATCH pattern] [COUNT count]`（BoundUtil 中可能需要通过原生 RedisTemplate 调用）

**用途：** 增量遍历大集合，避免 `SMEMBERS` 阻塞 Redis。

**使用场景：**

```java
// 遍历用户1的所有关注（适用于大集合）
Cursor<Long> cursor = redisTemplate.opsForSet().scan(
    "user:follow:1", 
    ScanOptions.scanOptions().match("*").count(100).build());
try {
    while (cursor.hasNext()) {
        Long userId = cursor.next();
        // 处理每个关注用户
    }
} finally {
    cursor.close();
}
```



## 使用示例

![image-20251117110731144](/Users/apple/Pictures/assets/image-20251117110731144.png)



![image-20251117110813350](/Users/apple/Pictures/assets/image-20251117110813350.png)



![image-20251117111001098](/Users/apple/Pictures/assets/image-20251117111001098.png)



![image-20251117111025956](/Users/apple/Pictures/assets/image-20251117111025956.png)





## 6. 社交媒体典型场景实现

### 场景1：关注/取消关注系统

**数据模型：**
- `user:follow:{userId}` - 用户关注的人（Set）
- `user:follower:{userId}` - 用户的粉丝（Set）

**实现：**

```java
public class FollowService {
    
    private final BoundUtil boundUtil;
    
    /**
     * 关注用户
     */
    public boolean follow(Long followerId, Long followedId) {
        if (followerId.equals(followedId)) {
            return false; // 不能关注自己
        }
        
        // 1. 添加到关注列表
        Long added = boundUtil.sAdd("user:follow:" + followerId, followedId);
        if (added > 0) {
            // 2. 添加到对方的粉丝列表
            boundUtil.sAdd("user:follower:" + followedId, followerId);
            
            // 3. 更新关注计数（可选，如果使用计数）
            boundUtil.incr("user:following_count:" + followerId);
            boundUtil.incr("user:follower_count:" + followedId);
            
            return true;
        }
        return false; // 已经关注过了
    }
    
    /**
     * 取消关注
     */
    public boolean unfollow(Long followerId, Long followedId) {
        Long removed = boundUtil.sRem("user:follow:" + followerId, followedId);
        if (removed > 0) {
            // 从对方粉丝列表移除
            boundUtil.sRem("user:follower:" + followedId, followerId);
            
            // 更新计数
            boundUtil.incrBy("user:following_count:" + followerId, -1);
            boundUtil.incrBy("user:follower_count:" + followedId, -1);
            
            return true;
        }
        return false;
    }
    
    /**
     * 检查是否关注
     */
    public boolean isFollowing(Long followerId, Long followedId) {
        return Boolean.TRUE.equals(
            boundUtil.sIsMember("user:follow:" + followerId, followedId));
    }
    
    /**
     * 获取关注列表
     */
    public Set<Long> getFollowing(Long userId) {
        return boundUtil.sMembers("user:follow:" + userId, Long.class);
    }
    
    /**
     * 获取粉丝列表
     */
    public Set<Long> getFollowers(Long userId) {
        return boundUtil.sMembers("user:follower:" + userId, Long.class);
    }
    
    /**
     * 获取关注数
     */
    public Long getFollowingCount(Long userId) {
        return boundUtil.sCard("user:follow:" + userId);
    }
    
    /**
     * 获取粉丝数
     */
    public Long getFollowerCount(Long userId) {
        return boundUtil.sCard("user:follower:" + userId);
    }
    
    /**
     * 获取共同关注
     */
    public Set<Long> getMutualFollowing(Long userId1, Long userId2) {
        // 使用 RedisTemplate 执行交集运算
        return redisTemplate.opsForSet().intersect(
            "user:follow:" + userId1, 
            "user:follow:" + userId2);
    }
    
    /**
     * 检查是否互相关注
     */
    public boolean isMutualFollowing(Long userId1, Long userId2) {
        return isFollowing(userId1, userId2) && isFollowing(userId2, userId1);
    }
}
```



### 场景2：点赞/取消点赞系统

**数据模型：**
- `post:like:{postId}` - 点赞该帖子的用户（Set）
- `user:like:{userId}` - 用户点赞的帖子（Set，可选）

**实现：**

```java
public class LikeService {
    
    private final BoundUtil boundUtil;
    
    /**
     * 点赞帖子
     */
    public boolean likePost(Long userId, Long postId) {
        // 1. 添加到帖子点赞列表
        Long added = boundUtil.sAdd("post:like:" + postId, userId);
        if (added > 0) {
            // 2. 添加到用户点赞列表（可选，用于查找用户点赞过的帖子）
            boundUtil.sAdd("user:like:" + userId, postId);
            
            // 3. 更新点赞计数
            boundUtil.incr("post:like_count:" + postId);
            
            return true;
        }
        return false; // 已经点赞过了
    }
    
    /**
     * 取消点赞
     */
    public boolean unlikePost(Long userId, Long postId) {
        Long removed = boundUtil.sRem("post:like:" + postId, userId);
        if (removed > 0) {
            // 从用户点赞列表移除
            boundUtil.sRem("user:like:" + userId, postId);
            
            // 更新点赞计数
            boundUtil.incrBy("post:like_count:" + postId, -1);
            
            return true;
        }
        return false;
    }
    
    /**
     * 检查是否已点赞
     */
    public boolean isLiked(Long userId, Long postId) {
        return Boolean.TRUE.equals(
            boundUtil.sIsMember("post:like:" + postId, userId));
    }
    
    /**
     * 获取点赞用户列表
     */
    public Set<Long> getLikers(Long postId) {
        return boundUtil.sMembers("post:like:" + postId, Long.class);
    }
    
    /**
     * 获取点赞数
     */
    public Long getLikeCount(Long postId) {
        return boundUtil.sCard("post:like:" + postId);
    }
    
    /**
     * 批量检查点赞状态（用于列表页）
     */
    public Map<Long, Boolean> batchCheckLiked(Long userId, List<Long> postIds) {
        Map<Long, Boolean> result = new HashMap<>();
        for (Long postId : postIds) {
            result.put(postId, isLiked(userId, postId));
        }
        return result;
    }
    
    /**
     * 获取用户点赞过的帖子列表
     */
    public Set<Long> getUserLikedPosts(Long userId) {
        return boundUtil.sMembers("user:like:" + userId, Long.class);
    }
    
    /**
     * 获取共同点赞的帖子（用户1和用户2都点赞的）
     */
    public Set<Long> getMutualLikedPosts(Long userId1, Long userId2) {
        return redisTemplate.opsForSet().intersect(
            "user:like:" + userId1,
            "user:like:" + userId2);
    }
}
```



### 场景3：收藏/取消收藏系统

**数据模型：**
- `user:favorite:{userId}` - 用户收藏的帖子（Set）
- `post:favorited_by:{postId}` - 收藏该帖子的用户（Set，可选）

**实现：**

```java
public class FavoriteService {
    
    private final BoundUtil boundUtil;
    
    /**
     * 收藏帖子
     */
    public boolean favoritePost(Long userId, Long postId) {
        Long added = boundUtil.sAdd("user:favorite:" + userId, postId);
        if (added > 0) {
            // 可选：反向索引，用于统计帖子的收藏数
            boundUtil.sAdd("post:favorited_by:" + postId, userId);
            boundUtil.incr("post:favorite_count:" + postId);
            return true;
        }
        return false;
    }
    
    /**
     * 取消收藏
     */
    public boolean unfavoritePost(Long userId, Long postId) {
        Long removed = boundUtil.sRem("user:favorite:" + userId, postId);
        if (removed > 0) {
            boundUtil.sRem("post:favorited_by:" + postId, userId);
            boundUtil.incrBy("post:favorite_count:" + postId, -1);
            return true;
        }
        return false;
    }
    
    /**
     * 检查是否已收藏
     */
    public boolean isFavorited(Long userId, Long postId) {
        return Boolean.TRUE.equals(
            boundUtil.sIsMember("user:favorite:" + userId, postId));
    }
    
    /**
     * 获取用户收藏列表
     */
    public Set<Long> getUserFavorites(Long userId) {
        return boundUtil.sMembers("user:favorite:" + userId, Long.class);
    }
    
    /**
     * 获取收藏数
     */
    public Long getFavoriteCount(Long postId) {
        return boundUtil.sCard("post:favorited_by:" + postId);
    }
}
```



### 场景4：黑名单系统

**数据模型：**
- `user:blacklist:{userId}` - 用户的黑名单（Set）
- `user:blocked_by:{userId}` - 被哪些用户拉黑（Set，可选）

**实现：**

```java
public class BlacklistService {
    
    private final BoundUtil boundUtil;
    
    /**
     * 拉黑用户
     */
    public boolean blockUser(Long userId, Long blockedUserId) {
        if (userId.equals(blockedUserId)) {
            return false;
        }
        
        Long added = boundUtil.sAdd("user:blacklist:" + userId, blockedUserId);
        if (added > 0) {
            // 可选：记录被拉黑关系
            boundUtil.sAdd("user:blocked_by:" + blockedUserId, userId);
            return true;
        }
        return false;
    }
    
    /**
     * 取消拉黑
     */
    public boolean unblockUser(Long userId, Long blockedUserId) {
        Long removed = boundUtil.sRem("user:blacklist:" + userId, blockedUserId);
        if (removed > 0) {
            boundUtil.sRem("user:blocked_by:" + blockedUserId, userId);
            return true;
        }
        return false;
    }
    
    /**
     * 检查是否在黑名单中
     */
    public boolean isBlocked(Long userId, Long blockedUserId) {
        return Boolean.TRUE.equals(
            boundUtil.sIsMember("user:blacklist:" + userId, blockedUserId));
    }
    
    /**
     * 过滤黑名单用户（用于Feed流）
     */
    public List<Long> filterBlacklisted(Long userId, List<Long> userIds) {
        Set<Long> blacklist = boundUtil.sMembers("user:blacklist:" + userId, Long.class);
        return userIds.stream()
            .filter(id -> !blacklist.contains(id))
            .collect(Collectors.toList());
    }
}
```



### 场景5：标签/话题系统

**数据模型：**
- `post:tags:{postId}` - 帖子的标签（Set）
- `tag:posts:{tagName}` - 标签下的帖子（Set）

**实现：**

```java
public class TagService {
    
    private final BoundUtil boundUtil;
    
    /**
     * 给帖子添加标签
     */
    public void addTagsToPost(Long postId, String... tags) {
        // 1. 添加到帖子的标签列表
        boundUtil.sAdd("post:tags:" + postId, (Object[]) tags);
        
        // 2. 添加到标签的帖子列表（反向索引）
        for (String tag : tags) {
            boundUtil.sAdd("tag:posts:" + tag, postId);
        }
    }
    
    /**
     * 移除帖子标签
     */
    public void removeTagsFromPost(Long postId, String... tags) {
        boundUtil.sRem("post:tags:" + postId, (Object[]) tags);
        for (String tag : tags) {
            boundUtil.sRem("tag:posts:" + tag, postId);
        }
    }
    
    /**
     * 获取帖子标签
     */
    public Set<String> getPostTags(Long postId) {
        return boundUtil.sMembers("post:tags:" + postId, String.class);
    }
    
    /**
     * 根据标签获取帖子列表
     */
    public Set<Long> getPostsByTag(String tag) {
        return boundUtil.sMembers("tag:posts:" + tag, Long.class);
    }
    
    /**
     * 查找包含多个标签的帖子（交集）
     */
    public Set<Long> getPostsByTags(String... tags) {
        if (tags.length == 0) {
            return Collections.emptySet();
        }
        
        // 将标签转换为key列表
        List<String> keys = Arrays.stream(tags)
            .map(tag -> "tag:posts:" + tag)
            .collect(Collectors.toList());
        
        // 计算交集
        return redisTemplate.opsForSet().intersect(keys);
    }
    
    /**
     * 获取标签下的帖子数
     */
    public Long getTagPostCount(String tag) {
        return boundUtil.sCard("tag:posts:" + tag);
    }
}
```



### 场景6：共同关注推荐

**数据模型：**
- `user:follow:{userId}` - 用户关注列表
- `mutual:follow:{userId1}:{userId2}` - 缓存共同关注（可选）

**实现：**

```java
public class RecommendService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 基于共同关注推荐用户
     */
    public List<Long> recommendUsersByMutualFollow(Long userId, int limit) {
        // 1. 获取用户关注列表
        Set<Long> following = boundUtil.sMembers("user:follow:" + userId, Long.class);
        if (following.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 2. 合并所有关注用户的关注列表（差集，排除已关注）
        Set<Long> candidates = new HashSet<>();
        for (Long followId : following) {
            Set<Long> theirFollowing = boundUtil.sMembers("user:follow:" + followId, Long.class);
            candidates.addAll(theirFollowing);
        }
        
        // 3. 排除自己已关注的
        candidates.removeAll(following);
        candidates.remove(userId); // 排除自己
        
        // 4. 按共同关注数排序推荐（简化版）
        Map<Long, Integer> scoreMap = new HashMap<>();
        for (Long candidateId : candidates) {
            // 计算共同关注数
            Set<Long> mutual = redisTemplate.opsForSet().intersect(
                "user:follow:" + userId,
                "user:follow:" + candidateId);
            scoreMap.put(candidateId, mutual.size());
        }
        
        // 5. 按共同关注数排序，返回前N个
        return scoreMap.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取共同关注的用户列表
     */
    public Set<Long> getMutualFollowing(Long userId1, Long userId2) {
        String cacheKey = "mutual:follow:" + userId1 + ":" + userId2;
        
        // 检查缓存
        Set<Long> cached = boundUtil.sMembers(cacheKey, Long.class);
        if (!cached.isEmpty()) {
            return cached;
        }
        
        // 计算交集
        Set<Long> mutual = redisTemplate.opsForSet().intersect(
            "user:follow:" + userId1,
            "user:follow:" + userId2);
        
        // 缓存结果（1小时）
        if (!mutual.isEmpty()) {
            mutual.forEach(id -> boundUtil.sAdd(cacheKey, id));
            redisTemplate.expire(cacheKey, 1, TimeUnit.HOURS);
        }
        
        return mutual;
    }
}
```



## 7. 性能优化建议

### 1. 大集合处理

**问题：** `SMEMBERS` 对于大集合（> 1万成员）会阻塞 Redis。

**解决方案：**
- 使用 `SSCAN` 增量遍历
- 使用 `SCARD` 检查集合大小，超过阈值时使用 `SSCAN`
- 考虑使用 ZSet 替代 Set（按时间排序的点赞列表）

```java
// 安全的获取成员方法
public Set<Long> safeGetMembers(String key, int threshold) {
    Long size = boundUtil.sCard(key);
    if (size != null && size > threshold) {
        // 使用 SCAN 遍历
        Set<Long> result = new HashSet<>();
        Cursor<Long> cursor = redisTemplate.opsForSet().scan(
            key, ScanOptions.scanOptions().count(100).build());
        try {
            while (cursor.hasNext()) {
                result.add(cursor.next());
            }
        } finally {
            cursor.close();
        }
        return result;
    } else {
        // 小集合直接使用 SMEMBERS
        return boundUtil.sMembers(key, Long.class);
    }
}
```



### 2. 集合运算优化

**问题：** `SINTER`、`SUNION`、`SDIFF` 对于大集合性能较差（时间复杂度 O(N*M)）。

**解决方案：**
- 限制参与运算的集合数量
- 使用 `SINTERSTORE` 缓存结果，避免重复计算
- 对于实时性要求不高的场景，异步计算并缓存

```java
// 缓存交集结果
public Set<Long> getCachedMutualFollowing(Long userId1, Long userId2) {
    String cacheKey = "mutual:follow:" + userId1 + ":" + userId2;
    
    // 检查缓存
    Set<Long> cached = boundUtil.sMembers(cacheKey, Long.class);
    if (!cached.isEmpty()) {
        return cached;
    }
    
    // 计算并缓存
    Long count = redisTemplate.opsForSet().intersectAndStore(
        "user:follow:" + userId1,
        "user:follow:" + userId2,
        cacheKey);
    
    // 设置过期时间
    redisTemplate.expire(cacheKey, 1, TimeUnit.HOURS);
    
    return boundUtil.sMembers(cacheKey, Long.class);
}
```



### 3. 计数一致性

**问题：** 同时使用 Set 和计数器可能导致不一致。

**解决方案：**
- **方案1：** 只使用 `SCARD` 获取计数，不使用独立计数器
- **方案2：** 使用 Lua 脚本保证原子性（添加成员 + 计数同时执行）
- **方案3：** 定期同步计数（定时任务）

```lua
-- 点赞Lua脚本（原子性保证）
local postId = ARGV[1]
local userId = ARGV[2]
local key = "post:like:" .. postId
local countKey = "post:like_count:" .. postId

local added = redis.call('SADD', key, userId)
if added == 1 then
    redis.call('INCR', countKey)
    return 1
else
    return 0
end
```



### 4. 内存优化

**问题：** 大量小集合占用内存。

**解决方案：**
- 设置合理的过期时间
- 对于不活跃的数据，定期清理
- 使用 Redis 的压缩功能（Redis 6.0+）

```java
// 设置过期时间
boundUtil.sAdd("user:follow:" + userId, followedId);
redisTemplate.expire("user:follow:" + userId, 30, TimeUnit.DAYS);
```



## 8. 业界常见实践（Set）

1. **关注关系存储**
   - 使用 Set 存储关注列表和粉丝列表
   - 双向维护：`user:follow:{userId}` 和 `user:follower:{userId}`
   - 使用 `SINTER` 实现共同关注功能

2. **点赞/收藏去重**
   - Set 天然去重，防止重复操作
   - 使用 `SISMEMBER` 快速检查状态
   - 结合计数器维护总数（保证一致性）

3. **标签系统**
   - 正向索引：`post:tags:{postId}` - 帖子的标签
   - 反向索引：`tag:posts:{tag}` - 标签下的帖子
   - 使用交集查找多标签匹配

4. **黑名单/白名单**
   - 使用 Set 存储黑名单，快速过滤
   - Feed 流中过滤黑名单用户

5. **空集合标记**
   - 使用 Set 标记空集合，避免频繁查询数据库（当前项目使用场景）
   - 例如：`user:post:empty:set` 标记没有帖子的用户

6. **大集合处理**
   - 超过 1 万成员使用 `SSCAN` 替代 `SMEMBERS`
   - 集合运算结果缓存，避免重复计算

7. **数据一致性**
   - 使用 Lua 脚本保证原子性
   - 定期同步计数和 Set 成员数

8. **过期时间管理**
   - 热点数据设置较长过期时间
   - 临时数据（如推荐列表）设置短过期时间
   - 活跃数据不过期或设置很长过期时间

