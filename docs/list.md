## 实现功能

使用Redis中的list命令，结合社交媒体时间线场景实现，实现了如下功能

- 功能一：发布新动态 (Posting an Update)<br>
    功能描述：用户（如 user:123）发布了一篇新动态（如 post:789）。这条动态需要立即出现在其个人时间线的最顶端。
- 功能二：查看个人时间线 (Viewing a Profile Feed)<br>
  功能描述：访问 user:123 的个人主页，需要分页加载他发布过的动态，按时间倒序（最新优先）。
- 功能三：保持时间线固定长度 (Capped Timeline)<br>
功能描述：为了节省 Redis 内存，我们不能无限期地在一个列表中存储一个用户的所有帖子。我们只想保留，比如说，最近 1000 条动态。
- 功能四：获取“关注”时间线 (Home Feed / Fan-out)<br>
功能描述：这是您App的主页，显示您所有关注的人的动态，并按时间混合排序。
- 功能五：删除动态 (Deleting an Update)<br>
功能描述：用户 user:123 删除了他之前发布的 post:456。这条动态必须从他的个人时间线中移除。
- 功能六：获取总动态数 (Post Count)<br>
功能描述：在用户 user:123 的个人资料页上显示“总共发布了 85 条动态”。



## 常见命令

| 命令          | 语法                                   | 作用                                       | 时间复杂度 | 应用示例                                |
| ------------- | -------------------------------------- | ------------------------------------------ | ---------- | --------------------------------------- |
| **LPUSH**     | `LPUSH key value [value ...]`          | 将一个或多个值插入列表头部                 | O(1)       | `LPUSH timeline:user1 post:123`         |
| **RPUSH**     | `RPUSH key value [value ...]`          | 将一个或多个值插入列表尾部                 | O(1)       | `RPUSH queue:tasks task:456`            |
| **LPOP**      | `LPOP key [count]`                     | 移除并返回列表头部元素                     | O(1)       | `LPOP timeline:user1`                   |
| **RPOP**      | `RPOP key [count]`                     | 移除并返回列表尾部元素                     | O(1)       | `RPOP queue:tasks`                      |
| **LRANGE**    | `LRANGE key start stop`                | 返回列表中指定区间元素                     | O(S+N)     | `LRANGE timeline:user1 0 9`             |
| **LINDEX**    | `LINDEX key index`                     | 通过索引获取列表中的元素                   | O(N)       | `LINDEX timeline:user1 0`               |
| **LLEN**      | `LLEN key`                             | 获取列表长度                               | O(1)       | `LLEN timeline:user1`                   |
| **LTRIM**     | `LTRIM key start stop`                 | 修剪列表，只保留指定区间                   | O(N)       | `LTRIM timeline:user1 0 999`            |
| **LREM**      | `LREM key count value`                 | 移除列表中与value相等的元素                | O(N)       | `LREM timeline:user1 1 post:456`        |
| **LSET**      | `LSET key index value`                 | 通过索引设置列表元素的值                   | O(N)       | `LSET mylist 0 "new_value"`             |
| **LINSERT**   | `LINSERT key BEFORE|AFTER pivot value` | 在元素前/后插入新元素                      | O(N)       | `LINSERT mylist BEFORE "world" "hello"` |
| **RPOPLPUSH** | `RPOPLPUSH source destination`         | 原子性地从源列表尾部弹出并插入目标列表头部 | O(1)       | `RPOPLPUSH queue:in queue:processing`   |
| **BRPOP**     | `BRPOP key [key ...] timeout`          | 阻塞式右弹出，列表为空时阻塞               | O(1)       | `BRPOP queue:tasks 30`                  |
| **BLPOP**     | `BLPOP key [key ...] timeout`          | 阻塞式左弹出，列表为空时阻塞               | O(1)       | `BLPOP queue:tasks 30`                  |



使用示例

![image-20251115135228601](/Users/apple/Pictures/assets/image-20251115135228601.png)

![image-20251115135525704](/Users/apple/Pictures/assets/image-20251115135525704.png)

![image-20251115135602944](/Users/apple/Pictures/assets/image-20251115135602944.png)



### 性能特点总结

- **头部操作**：LPUSH/LPOP - O(1)，非常高效
- **尾部操作**：RPUSH/RPOP - O(1)，非常高效
- **随机访问**：LINDEX/LSET - O(N)，避免在大列表中使用
- **范围查询**：LRANGE - O(S+N)，S是起始偏移量，N是指定范围
- **修剪操作**：LTRIM - O(N)，需要谨慎使用在大列表上



### 使用建议

1. **小列表优先**：List在元素较少时性能最佳
2. **避免大范围LRANGE**：一次性获取大量数据会影响性能
3. **合理使用LTRIM**：定期修剪控制列表大小
4. **利用阻塞操作**：BRPOP/BLPOP适合实时消息处理
5. **原子操作**：RPOPLPUSH适合需要事务性的场景



## 使用说明

由于Redis基于内存，空间有限。list并不直接存储所有数据，如一个用户所有的帖子。而是扮演一个类似关系表的形式。使用 `post:id` 存储具体的帖子信息，结合 `user:id` 存储发过的帖子，value 是 `[1, 3, 8]`

> 实际开发中 String、hash两种类型用于存储数据，list、set、sortedset用于存储关系数据，如list存储有序关系，set存储无序关系
>
> | 数据结构       | 存储内容     | 典型案例         | 特点                 |
> | -------------- | ------------ | ---------------- | -------------------- |
> | **List**       | 有序关系ID   | 时间线、消息队列 | 顺序重要，可重复     |
> | **Set**        | 无序唯一关系 | 关注关系、标签   | 去重，快速判断存在性 |
> | **Sorted Set** | 带权重关系   | 排行榜、优先级   | 按分数排序，范围查询 |
> | **String**     | 具体信息     | 对象缓存、计数器 | 简单KV，整存整取     |
> | **Hash**       | 可修改对象   | 用户资料、购物车 | 部分更新，字段操作   |

添加数据时往往需要拆分，分别执行两条 Redis 命令，向两个数据中添加信息。业界通常使用 lua 脚本解决这个问题



取 list 中所有元素有两种方式

- 分两条 Redis 命令，先使用 `lsize` 获取总长度，再使用 `lrange` 获取所有元素
- 使用 `LRANGE 0 -1` 一次性获取所有元素（**使用较多**）

> Redis 中下标使用与 python 十分相似，分为正向和反向。**唯一的不同之处在于 Redis 中的 `[a, b]` 会返回a到b（包括b）的所有元素**
>
> - **正向索引**：从 `0` 开始，`0` 表示第一个元素
> - **负向索引**：从 `-1` 开始，`-1` 表示最后一个元素
>
> **重要区别**：Redis 的 `LRANGE 0 -1` 会返回**整个列表**，而 Python 的 `lst[0:-1]` 会返回**除最后一个元素外**的所有元素。



Redis中大多数情况下设置的过期时间是固定的，并不会因为访问或修改而更新过期时间，如果要更新，可以在最后加上一句 `expire` 命令



### 分页查询策略 (getUserPagedPosts)

```java
// 判断是否在Redis缓存范围内
if (end < USER_POST_LIST_MAX_SIZE) {
  // 在Redis缓存范围内，从Redis获取（快速）
  return getUserPagedPostsFromCache(userId, key, start, end);
} else {
  // 超出Redis缓存范围，从数据库查询（完整数据）
  return getUserPagedPostsFromDatabase(userId, page, pageSize);
}
```

策略说明：

- 前 N 条（0 到 N-1）：从 Redis 获取，速度快

- 历史数据（N 条之后）：从数据库查询，数据完整

#### 2. 初始化策略优化

- 只加载最新的 USER_POST_LIST_MAX_SIZE 条到 Redis

- 不加载所有帖子，控制内存使用

- 保证首页访问速度

#### 3. 添加帖子时的处理

- 使用 Lua 脚本在添加时自动维护上限

- 超过上限时删除最旧的帖子（尾部）

- 不在查询时截断，保留用户查看历史的能力

### 业界常见做法对比

| 方案                                           | 优点                                                   | 缺点             | 适用场景     |
| :--------------------------------------------- | :----------------------------------------------------- | :--------------- | :----------- |
| 每次都LTRIM截断                                | 严格控制内存                                           | 用户无法查看历史 | 不推荐       |
| 先不管，等用户查看<br>(每次查询都加载进Redis） | 保留完整数据                                           | 内存可能无限增长 | 小规模场景   |
| 混合策略（推荐）                               | 1. 首页快速<br>2. 历史完整<br>3. 内存可控<br>4. 体验好 | 实现稍复杂       | 生产环境推荐 |

### 优势

1. 性能：首页访问从 Redis 获取，延迟低

1. 完整性：历史数据从数据库查询，数据完整

1. 内存可控：只缓存最新 N 条，内存占用可控

1. 用户体验：可查看所有历史数据

1. 灵活性：可根据业务调整缓存大小



- 功能一：发布新动态 (Posting an Update)<br>
  功能描述：用户（如 user:123）发布了一篇新动态（如 post:789）。这条动态需要立即出现在其个人时间线的最顶端。
- 功能二：查看个人时间线 (Viewing a Profile Feed)<br>
  功能描述：访问 user:123 的个人主页，需要分页加载他发布过的动态，按时间倒序（最新优先）。
- 功能三：保持时间线固定长度 (Capped Timeline)<br>
  功能描述：为了节省 Redis 内存，我们不能无限期地在一个列表中存储一个用户的所有帖子。我们只想保留，比如说，最近 1000 条动态。
- 功能四：获取“关注”时间线 (Home Feed / Fan-out)<br>
  功能描述：这是您App的主页，显示您所有关注的人的动态，并按时间混合排序。
- 功能五：删除动态 (Deleting an Update)<br>
  功能描述：用户 user:123 删除了他之前发布的 post:456。这条动态必须从他的个人时间线中移除。
- 功能六：获取总动态数 (Post Count)<br>
  功能描述：在用户 user:123 的个人资料页上显示“总共发布了 85 条动态”。