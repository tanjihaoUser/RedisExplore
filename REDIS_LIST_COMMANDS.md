# Redis List 命令列表



本文档详细说明所有 Redis List 命令的使用场景和示例代码。



## 1. 基础操作

### LPUSH - 左侧推入

**Redis命令：** `LPUSH key element [element ...]`

**方法签名：**
```java
boundUtil.leftPush(key, value1, value2, ...);
```

**用途：** 向列表头部添加一个或多个元素

**参数说明：**
- `key`: 列表的键名
- `value1, value2, ...`: 要推入的一个或多个元素（可变参数）

**使用场景：**

1. **时间线/动态流（Timeline）**
   - 新内容总是插入到列表头部，保证最新内容在前
   ```java
   // 发布新帖子，插入到用户帖子列表头部
   boundUtil.leftPush("user:post:rel:1", postId);
   ```

2. **消息队列（FIFO队列）**
   - 生产者从左侧推入，消费者从右侧弹出
   ```java
   // 发送消息到队列
   boundUtil.leftPush("queue:messages", messageId);
   ```

3. **最近访问记录**
   - 记录用户最近访问的页面，新访问插入头部
   ```java
   // 记录用户最近访问的页面
   boundUtil.leftPush("user:recent:pages:1", pageId);
   ```

4. **操作日志**
   - 记录用户操作历史，最新操作在前
   ```java
   // 记录用户操作
   boundUtil.leftPush("user:actions:1", actionId);
   ```




### RPUSH - 右侧推入

**Redis命令：** `RPUSH key element [element ...]`

**方法签名：**
```java
boundUtil.rightPush(key, value1, value2, ...);
```

**用途：** 向列表尾部添加一个或多个元素

**参数说明：**
- `key`: 列表的键名
- `value1, value2, ...`: 要推入的一个或多个元素（可变参数）

**使用场景：**

1. **队列（Queue）**
   - 标准队列实现，尾部入队
   ```java
   // 任务入队
   boundUtil.rightPush("queue:tasks", taskId);
   ```

2. **初始化列表**
   - 批量初始化列表数据
   ```java
   // 初始化用户帖子列表（从数据库加载）
   Long[] postIds = {1L, 2L, 3L, 4L, 5L};
   boundUtil.rightPush("user:post:rel:1", postIds);
   ```

3. **追加日志**
   - 按时间顺序追加日志条目
   ```java
   // 追加系统日志
   boundUtil.rightPush("system:logs", logEntry);
   ```




### LPOP - 左侧弹出

**Redis命令：** `LPOP key`

**方法签名：**
```java
boundUtil.leftPop(key, Class<T> clazz);
```

**用途：** 移除并返回列表头部的元素

**参数说明：**
- `key`: 列表的键名
- `clazz`: 返回值的类型

**返回值：** 列表头部的元素，如果列表为空返回 `null`

**使用场景：**

1. **栈（Stack）实现**
   - LIFO（后进先出）数据结构
   ```java
   // 实现撤销操作栈
   String lastAction = boundUtil.leftPop("user:undo:stack:1", String.class);
   ```

2. **消息队列消费**
   - 从队列头部消费消息
   ```java
   // 消费消息
   String message = boundUtil.leftPop("queue:messages", String.class);
   ```

3. **获取最新元素**
   - 获取并移除列表中最新的元素
   ```java
   // 获取用户最新的一条动态
   Long latestPostId = boundUtil.leftPop("user:posts:1", Long.class);
   ```




### RPOP - 右侧弹出

**Redis命令：** `RPOP key`

**方法签名：**
```java
boundUtil.rightPop(key, Class<T> clazz);
```

**用途：** 移除并返回列表尾部的元素

**参数说明：**
- `key`: 列表的键名
- `clazz`: 返回值的类型

**返回值：** 列表尾部的元素，如果列表为空返回 `null`

**使用场景：**

1. **队列消费**
   - 标准队列实现，尾部出队
   ```java
   // 从任务队列取出任务
   String taskId = boundUtil.rightPop("queue:tasks", String.class);
   ```

2. **获取最旧元素**
   - 获取并移除列表中最旧的元素
   ```java
   // 获取最旧的日志条目
   String oldLog = boundUtil.rightPop("system:logs", String.class);
   ```




### LRANGE - 获取列表

**Redis命令：** `LRANGE key start stop`

**方法签名：**
```java
boundUtil.range(key, start, end, Class<T> clazz);
```

**用途：** 获取列表中指定范围的元素

**参数说明：**
- `key`: 列表的键名
- `start`: 起始索引（0-based，包含）
- `end`: 结束索引（0-based，包含）
- `clazz`: 返回值的类型

**索引说明：**
- 索引从0开始，0表示第一个元素
- 支持负数索引：
  - `-1` 表示最后一个元素
  - `-2` 表示倒数第二个元素
  - 以此类推
- 特殊用法：
  - `LRANGE key 0 -1` 获取整个列表的所有元素
  - `LRANGE key 0 9` 获取前10个元素（索引0到9）
  - `LRANGE key -10 -1` 获取最后10个元素

**返回值：** 指定范围内的元素列表，如果范围无效或列表为空返回空列表

**使用场景：**

1. **分页查询**
   - 实现列表的分页功能
   ```java
   // 获取用户第1页的帖子（每页10条）
   int page = 1;
   int pageSize = 10;
   int start = (page - 1) * pageSize;
   int end = start + pageSize - 1;
   List<Long> postIds = boundUtil.range("user:post:rel:1", start, end, Long.class);
   ```

2. **获取最新N条**
   - 获取列表头部的最新N条记录
   ```java
   // 获取用户最新的10条动态
   List<Long> recentPosts = boundUtil.range("user:posts:1", 0, 9, Long.class);
   ```

3. **获取历史记录**
   - 获取列表尾部的历史记录
   ```java
   // 获取最旧的10条日志
   Long listSize = boundUtil.listSize("system:logs");
   List<String> oldLogs = boundUtil.range("system:logs", 
       listSize - 10, listSize - 1, String.class);
   ```

4. **获取全部元素**
   - 获取整个列表（注意：大列表性能较差）
   ```java
   // 获取所有元素（不推荐用于大列表）
   List<String> allItems = boundUtil.range("list:items", 0, -1, String.class);
   ```




### LLEN - 获取长度

**Redis命令：** `LLEN key`

**方法签名：**
```java
boundUtil.listSize(key);
```

**用途：** 获取列表中元素的数量

**参数说明：**
- `key`: 列表的键名

**返回值：** 列表的长度（元素数量），如果key不存在返回 `0`

**使用场景：**

1. **统计和计数**
   - 统计列表中的元素数量
   ```java
   // 统计用户帖子数量
   Long postCount = boundUtil.listSize("user:post:rel:1");
   ```

2. **分页计算**
   - 计算分页的总页数
   ```java
   Long totalSize = boundUtil.listSize("user:posts:1");
   int pageSize = 10;
   int totalPages = (int) Math.ceil(totalSize.doubleValue() / pageSize);
   ```

3. **判断列表是否为空**
   - 检查列表是否有数据
   ```java
   Long size = boundUtil.listSize("queue:tasks");
   if (size == null || size == 0) {
       // 队列为空
   }
   ```

4. **限制检查**
   - 检查列表是否超过限制
   ```java
   Long size = boundUtil.listSize("user:recent:pages:1");
   if (size > 100) {
       // 超过限制，需要清理
   }
   ```




### LTRIM - 修剪列表

**Redis命令：** `LTRIM key start stop`

**方法签名：**
```java
boundUtil.trim(key, start, end);
```

**用途：** 只保留列表中指定范围的元素，删除其他元素

**参数说明：**
- `key`: 列表的键名
- `start`: 起始索引（0-based，包含）
- `end`: 结束索引（0-based，包含）

**索引说明：**
- 索引从0开始，0表示第一个元素
- 支持负数索引：
  - `-1` 表示最后一个元素
  - `-2` 表示倒数第二个元素
  - 以此类推
- 特殊用法：
  - `LTRIM key 0 99` 只保留前100个元素（索引0到99）
  - `LTRIM key -100 -1` 只保留最后100个元素

**注意：** 
- 此操作会**永久删除**范围外的元素，不可恢复
- 如果 `start > end` 或 `start` 超出列表范围，列表会被清空

**使用场景：**

1. **限制列表大小**
   - 只保留最新的N条记录，删除旧数据
   ```java
   // 只保留最新的100条帖子
   boundUtil.trim("user:post:rel:1", 0, 99);
   ```

2. **内存管理**
   - 定期清理历史数据，控制内存使用
   ```java
   // 定期清理，只保留最近7天的日志
   Long size = boundUtil.listSize("system:logs");
   if (size > 1000) {
       boundUtil.trim("system:logs", 0, 999);
   }
   ```

3. **滑动窗口**
   - 实现固定大小的滑动窗口
   ```java
   // 维护固定大小的最近访问记录
   boundUtil.leftPush("user:recent:pages:1", newPageId);
   boundUtil.trim("user:recent:pages:1", 0, 49); // 只保留50条
   ```




## 2. 元素操作

### LREM - 移除指定值

**Redis命令：** `LREM key count element`

**方法签名：**

```java
boundUtil.listRemove(key, value, count);      // 移除指定数量的元素
boundUtil.listRemoveAll(key, value);          // 移除所有匹配的元素（count=0）
```

**用途：** 从列表中移除指定值的元素

**参数说明：**

- `key`: 列表的键名
- `value`: 要移除的元素值
- `count`: 移除的数量，说明
  - **`count > 0`**: 从表头（左侧）开始，移除 `count` 个值为 `value` 的元素
    - 例如：`LREM key 2 "a"` 从头部开始移除2个值为"a"的元素
  - **`count < 0`**: 从表尾（右侧）开始，移除 `|count|` 个值为 `value` 的元素
    - 例如：`LREM key -2 "a"` 从尾部开始移除2个值为"a"的元素
  - **`count = 0`**: 移除**所有**值为 `value` 的元素
    - 例如：`LREM key 0 "a"` 移除所有值为"a"的元素

**返回值：** 被移除元素的数量

**使用建议：**
- 如果列表中有重复元素，使用 `count = 0` 可以一次性移除所有匹配项
- 如果只需要移除第一个匹配项，使用 `count = 1`
- 如果需要移除最后几个匹配项，使用负数 `count`

**使用场景：**

1. **删除特定元素**
   - 从列表中删除指定的帖子ID
   ```java
   // 删除用户帖子列表中的某个帖子
   boundUtil.listRemoveAll("user:post:rel:1", postId);
   ```

2. **取消操作**
   - 从操作历史中移除某个操作
   ```java
   // 从撤销栈中移除特定操作
   boundUtil.listRemove("user:undo:stack:1", actionId, 1);
   ```

3. **清理重复数据**
   - 移除列表中的重复元素
   ```java
   // 移除所有重复的访问记录
   boundUtil.listRemoveAll("user:recent:pages:1", duplicatePageId);
   ```

4. **批量删除**
   - 删除多个匹配的元素
   ```java
   // 删除前3个匹配的元素
   boundUtil.listRemove("queue:tasks", taskId, 3);
   ```




### LINDEX - 获取指定索引的元素

**Redis命令：** `LINDEX key index`

**方法签名：**
```java
boundUtil.listIndex(key, index, Class<T> clazz);
```

**用途：** 获取列表中指定索引位置的元素（不删除）

**参数说明：**
- `key`: 列表的键名
- `index`: 索引位置
- `clazz`: 返回值的类型

**索引说明：**
- 索引从0开始，0表示第一个元素
- 支持负数索引：
  - `-1` 表示最后一个元素
  - `-2` 表示倒数第二个元素
  - 以此类推

**返回值：** 指定位置的元素，如果索引超出范围返回 `null`

**性能注意：** LINDEX 是 O(N) 操作，对于大列表性能较差，应避免频繁使用。

**使用场景：**

1. **随机访问**
   - 获取列表中特定位置的元素
   ```java
   // 获取第一条帖子
   Long firstPost = boundUtil.listIndex("user:post:rel:1", 0, Long.class);
   
   // 获取最后一条帖子
   Long lastPost = boundUtil.listIndex("user:post:rel:1", -1, Long.class);
   ```

2. **检查元素是否存在**
   - 检查某个位置是否有元素
   ```java
   // 检查是否有置顶帖子（索引0）
   Long pinnedPost = boundUtil.listIndex("user:posts:pinned:1", 0, Long.class);
   if (pinnedPost != null) {
       // 有置顶帖子
   }
   ```

3. **获取特定位置的元素**
   - 获取排行榜中特定排名的元素
   ```java
   // 获取排行榜第10名
   String rank10 = boundUtil.listIndex("leaderboard", 9, String.class);
   ```

**性能注意：** LINDEX 是 O(N) 操作，对于大列表性能较差，应避免频繁使用。




### LSET - 设置指定索引的元素

**Redis命令：** `LSET key index element`

**方法签名：**
```java
boundUtil.listSet(key, index, value);
```

**用途：** 设置列表中指定索引位置的元素值

**参数说明：**
- `key`: 列表的键名
- `index`: 索引位置（0-based）
- `value`: 要设置的新值

**索引说明：**
- 索引从0开始，0表示第一个元素
- 支持负数索引：
  - `-1` 表示最后一个元素
  - `-2` 表示倒数第二个元素
  - 以此类推

**注意：** 
- 索引必须已存在，如果索引超出范围会抛出异常
- 此操作会**覆盖**原有元素的值

**使用场景：**

1. **更新特定位置的数据**
   - 更新列表中某个位置的值
   ```java
   // 更新置顶帖子
   boundUtil.listSet("user:posts:pinned:1", 0, newPinnedPostId);
   ```

2. **修复数据**
   - 修复列表中错误的数据
   ```java
   // 修复索引5处的错误数据
   boundUtil.listSet("user:posts:1", 5, correctedPostId);
   ```

3. **批量更新**
   - 更新多个位置的数据
   ```java
   // 更新排行榜前10名
   for (int i = 0; i < 10; i++) {
       boundUtil.listSet("leaderboard", i, newRankings[i]);
   }
   ```

**注意：** 索引必须已存在，否则会报错。




### LINSERT - 在指定元素前后插入

**Redis命令：** `LINSERT key BEFORE|AFTER pivot element`

**方法签名：**
```java
boundUtil.listInsert(key, pivot, value, true);   // 在pivot前面插入（BEFORE）
boundUtil.listInsert(key, pivot, value, false);  // 在pivot后面插入（AFTER）
```

**用途：** 在列表中指定元素的前面或后面插入新元素

**参数说明：**
- `key`: 列表的键名
- `pivot`: 参考元素（在哪个元素前后插入）
- `value`: 要插入的新元素
- `before`: 
  - `true` 表示在 `pivot` **前面**插入（BEFORE）
  - `false` 表示在 `pivot` **后面**插入（AFTER）

**返回值：** 
- 插入后列表的长度
- 如果 `pivot` 不存在，返回 `-1`

**注意：**
- 如果列表中有多个相同的 `pivot` 值，只会在**第一个匹配的元素**前后插入
- 此操作是 O(N) 复杂度，对于大列表性能较差

**使用场景：**

1. **有序插入**
   - 在特定元素前后插入新元素
   ```java
   // 在置顶帖子后插入新帖子
   boundUtil.listInsert("user:posts:1", pinnedPostId, newPostId, false);
   ```

2. **优先级队列**
   - 根据优先级插入元素
   ```java
   // 在高优先级任务后插入新任务
   boundUtil.listInsert("queue:tasks", highPriorityTaskId, newTaskId, false);
   ```

3. **插入分隔符**
   - 在特定元素前后插入分隔符或标记
   ```java
   // 在某个帖子后插入分隔符
   boundUtil.listInsert("user:posts:1", separatorPostId, "
   ", false);
   ```

**性能注意：** LINSERT 是 O(N) 操作，对于大列表性能较差。




## 3. 阻塞操作

### BLPOP - 阻塞式从左侧弹出

**Redis命令：** `BLPOP key [key ...] timeout`

**方法签名：**
```java
boundUtil.blockLeftPop(key, timeout, TimeUnit.SECONDS, Class<T> clazz);
```

**用途：** 如果列表为空，会阻塞等待直到有元素可用或超时

**参数说明：**
- `key`: 列表的键名（Redis原生支持多个key，但BoundUtil当前只支持单个key）
- `timeout`: 超时时间
- `unit`: 时间单位
- `clazz`: 返回值的类型

**行为说明：**
- 如果列表**不为空**，立即返回并移除列表头部的元素
- 如果列表**为空**，阻塞等待直到：
  - 有其他客户端向列表推入元素（立即返回）
  - 或达到超时时间（返回 `null`）
- `timeout = 0` 表示无限等待（不推荐）

**返回值：** 
- 列表头部的元素（如果列表不为空或等待期间有新元素）
- `null`（如果超时且列表仍为空）

**使用场景：**

1. **消息队列（阻塞消费）**
   - 消费者阻塞等待新消息
   ```java
   // 阻塞式消费消息，最多等待10秒
   String message = boundUtil.blockLeftPop("queue:messages", 10, TimeUnit.SECONDS, String.class);
   if (message != null) {
       // 处理消息
   } else {
       // 超时，没有新消息
   }
   ```

2. **任务队列**
   - 工作者阻塞等待新任务
   ```java
   // 工作者等待任务
   String taskId = boundUtil.blockLeftPop("queue:tasks", 30, TimeUnit.SECONDS, String.class);
   if (taskId != null) {
       processTask(taskId);
   }
   ```

3. **实时通知**
   - 实时等待通知消息
   ```java
   // 等待用户通知
   String notification = boundUtil.blockLeftPop("user:notifications:1", 60, TimeUnit.SECONDS, String.class);
   ```

4. **多队列监听**
   - 可以监听多个队列（Redis原生支持，但BoundUtil需要扩展）
   ```java
   // 监听多个队列，哪个有消息就处理哪个
   // 注意：这需要直接使用RedisTemplate的opsForList().leftPop(key, timeout, unit)
   ```

**优势：** 不会占用CPU，适合长时间等待的场景。




### BRPOP - 阻塞式从右侧弹出

**Redis命令：** `BRPOP key [key ...] timeout`

**方法签名：**
```java
boundUtil.blockRightPop(key, timeout, TimeUnit.SECONDS, Class<T> clazz);
```

**用途：** 如果列表为空，会阻塞等待直到有元素可用或超时

**参数说明：**
- `key`: 列表的键名（Redis原生支持多个key，但BoundUtil当前只支持单个key）
- `timeout`: 超时时间
- `unit`: 时间单位
- `clazz`: 返回值的类型

**行为说明：**
- 如果列表**不为空**，立即返回并移除列表尾部的元素
- 如果列表**为空**，阻塞等待直到：
  - 有其他客户端向列表推入元素（立即返回）
  - 或达到超时时间（返回 `null`）
- `timeout = 0` 表示无限等待（不推荐）

**返回值：** 
- 列表尾部的元素（如果列表不为空或等待期间有新元素）
- `null`（如果超时且列表仍为空）

**使用场景：**

1. **FIFO队列（阻塞消费）**
   - 从队列尾部阻塞消费
   ```java
   // 阻塞式从队列尾部消费
   String task = boundUtil.blockRightPop("queue:tasks", 10, TimeUnit.SECONDS, String.class);
   ```

2. **延迟任务处理**
   - 处理延迟队列中的任务
   ```java
   // 处理延迟任务
   String delayedTask = boundUtil.blockRightPop("queue:delayed", 60, TimeUnit.SECONDS, String.class);
   ```

3. **日志处理**
   - 实时处理日志队列
   ```java
   // 处理日志队列
   String logEntry = boundUtil.blockRightPop("queue:logs", 5, TimeUnit.SECONDS, String.class);
   ```




## 4. 原子移动操作

### RPOPLPUSH - 从源列表弹出并推入目标列表

**Redis命令：** `RPOPLPUSH source destination`

**方法签名：**
```java
boundUtil.rightPopAndLeftPush(sourceKey, destKey, Class<T> clazz);
```

**用途：** 原子性地从源列表右侧弹出元素，并推入目标列表左侧

**参数说明：**
- `sourceKey`: 源列表的键名
- `destKey`: 目标列表的键名
- `clazz`: 返回值的类型

**行为说明：**
- **原子操作**：这是一个原子操作，保证数据一致性
- 从源列表的**右侧**（尾部）弹出元素
- 推入目标列表的**左侧**（头部）
- 如果源列表为空，返回 `null`，不执行任何操作
- **特殊用法**：如果 `sourceKey` 和 `destKey` 相同，可以实现循环队列

**返回值：** 被移动的元素，如果源列表为空返回 `null`

**使用场景：**

1. **可靠消息队列**
   - 消息从待处理队列移到处理中队列
   ```java
   // 从待处理队列取出消息，移到处理中队列
   String message = boundUtil.rightPopAndLeftPush("queue:pending", "queue:processing", String.class);
   if (message != null) {
       try {
           processMessage(message);
           // 处理成功，从处理中队列移除
           boundUtil.listRemove("queue:processing", message, 1);
       } catch (Exception e) {
           // 处理失败，可以重新放回待处理队列
           boundUtil.leftPush("queue:pending", message);
       }
   }
   ```

2. **任务队列（可靠处理）**
   - 任务从待处理队列移到处理中队列
   ```java
   // 原子性地移动任务
   String taskId = boundUtil.rightPopAndLeftPush("queue:tasks:pending", "queue:tasks:processing", String.class);
   ```

3. **循环列表**
   - 实现循环队列
   ```java
   // 循环队列：元素从尾部移到头部
   String item = boundUtil.rightPopAndLeftPush("queue:circular", "queue:circular", String.class);
   ```

4. **轮询处理**
   - 多个工作者轮询处理任务
   ```java
   // 从主队列取出任务，移到当前工作者的处理队列
   String task = boundUtil.rightPopAndLeftPush("queue:main", "queue:worker:1", String.class);
   ```

**优势：** 原子操作，保证数据一致性，不会丢失消息。




### BRPOPLPUSH - 阻塞式弹出并推入

**Redis命令：** `BRPOPLPUSH source destination timeout`

**方法签名：**
```java
boundUtil.blockRightPopAndLeftPush(sourceKey, destKey, timeout, TimeUnit.SECONDS, Class<T> clazz);
```

**用途：** 如果源列表为空，会阻塞等待直到有元素可用或超时

**参数说明：**
- `sourceKey`: 源列表的键名
- `destKey`: 目标列表的键名
- `timeout`: 超时时间
- `unit`: 时间单位
- `clazz`: 返回值的类型

**行为说明：**
- **原子操作**：这是一个原子操作，保证数据一致性
- 如果源列表**不为空**，立即执行 RPOPLPUSH 操作
- 如果源列表**为空**，阻塞等待直到：
  - 有其他客户端向源列表推入元素（立即执行操作并返回）
  - 或达到超时时间（返回 `null`）
- `timeout = 0` 表示无限等待（不推荐）

**返回值：** 
- 被移动的元素（如果源列表不为空或等待期间有新元素）
- `null`（如果超时且源列表仍为空）

**使用场景：**

1. **可靠消息队列（阻塞版）**
   - 阻塞等待新消息，然后原子性移动
   ```java
   // 阻塞等待消息，然后移到处理队列
   String message = boundUtil.blockRightPopAndLeftPush(
       "queue:pending", 
       "queue:processing", 
       30, 
       TimeUnit.SECONDS, 
       String.class
   );
   ```

2. **任务处理系统**
   - 工作者阻塞等待任务，然后原子性移动到处理队列
   ```java
   // 工作者等待任务
   String task = boundUtil.blockRightPopAndLeftPush(
       "queue:tasks:pending",
       "queue:tasks:processing:worker1",
       60,
       TimeUnit.SECONDS,
       String.class
   );
   ```

3. **实时数据处理**
   - 实时处理数据流
   ```java
   // 实时处理数据
   String data = boundUtil.blockRightPopAndLeftPush(
       "queue:data:input",
       "queue:data:processing",
       10,
       TimeUnit.SECONDS,
       String.class
   );
   ```




## 5. 综合使用场景

### 场景1：社交媒体的时间线（Timeline）

```java
// 1. 发布新帖子
boundUtil.leftPush("user:post:rel:1", postId);

// 2. 获取用户最新的10条帖子
List<Long> postIds = boundUtil.range("user:post:rel:1", 0, 9, Long.class);

// 3. 限制列表大小（只保留最新的100条）
boundUtil.trim("user:post:rel:1", 0, 99);

// 4. 删除帖子
boundUtil.listRemoveAll("user:post:rel:1", postId);

// 5. 获取第一条帖子（最新）
Long latestPost = boundUtil.listIndex("user:post:rel:1", 0, Long.class);
```

### 场景2：消息队列系统

```java
// 生产者：发送消息
boundUtil.leftPush("queue:messages", messageId);

// 消费者：阻塞式消费消息
String message = boundUtil.blockLeftPop("queue:messages", 10, TimeUnit.SECONDS, String.class);
if (message != null) {
    processMessage(message);
}
```

### 场景3：可靠任务队列

```java
// 1. 添加任务到待处理队列
boundUtil.leftPush("queue:tasks:pending", taskId);

// 2. 工作者：原子性地从待处理队列取出任务，移到处理中队列
String taskId = boundUtil.rightPopAndLeftPush(
    "queue:tasks:pending", 
    "queue:tasks:processing:worker1", 
    String.class
);

// 3. 处理任务
try {
    processTask(taskId);
    // 处理成功，从处理中队列移除
    boundUtil.listRemove("queue:tasks:processing:worker1", taskId, 1);
} catch (Exception e) {
    // 处理失败，重新放回待处理队列
    boundUtil.leftPush("queue:tasks:pending", taskId);
    boundUtil.listRemove("queue:tasks:processing:worker1", taskId, 1);
}
```

### 场景4：最近访问记录

```java
// 1. 记录访问
boundUtil.leftPush("user:recent:pages:1", pageId);

// 2. 限制大小（只保留最近50条）
boundUtil.trim("user:recent:pages:1", 0, 49);

// 3. 获取最近访问的10条
List<String> recentPages = boundUtil.range("user:recent:pages:1", 0, 9, String.class);

// 4. 删除特定访问记录
boundUtil.listRemoveAll("user:recent:pages:1", pageId);
```

### 场景5：操作历史（撤销/重做）

```java
// 1. 记录操作
boundUtil.leftPush("user:undo:stack:1", actionId);

// 2. 撤销操作（弹出最新操作）
String lastAction = boundUtil.leftPop("user:undo:stack:1", String.class);
if (lastAction != null) {
    undoAction(lastAction);
    // 移到重做栈
    boundUtil.leftPush("user:redo:stack:1", lastAction);
}

// 3. 重做操作
String redoAction = boundUtil.leftPop("user:redo:stack:1", String.class);
if (redoAction != null) {
    redoAction(redoAction);
    // 移回撤销栈
    boundUtil.leftPush("user:undo:stack:1", redoAction);
}
```




## 6. 性能考虑

1. **LRANGE**：O(S+N)，S是start偏移量，N是元素数量
   - 对于大列表，避免使用大的范围
   - 建议：只获取需要的范围，不要获取整个列表

2. **LTRIM**：O(N)，N是要删除的元素数量
   - 频繁使用可能影响性能
   - 建议：定期批量清理，而不是每次操作都清理

3. **LINSERT**：O(N)，N是列表长度
   - 对于大列表，性能较差
   - 建议：避免在大列表中使用，或考虑使用有序集合（Sorted Set）

4. **LINDEX**：O(N)，N是索引位置
   - 对于大列表，性能较差
   - 建议：避免频繁使用，考虑使用Hash存储索引映射

5. **阻塞操作**：BLPOP/BRPOP不会占用CPU
   - 适合长时间等待的场景
   - 建议：合理设置超时时间，避免无限等待




## 7. 最佳实践

1. **限制列表大小**
   - 使用LTRIM定期清理，避免列表无限增长
   - 建议：设置合理的上限（如100-1000条）

2. **批量操作**
   - 使用LPUSH/RPUSH的批量版本，减少网络往返
   - 建议：一次推送多个元素，而不是多次推送单个元素

3. **原子操作**
   - 使用RPOPLPUSH等原子操作，保证数据一致性
   - 建议：关键操作使用原子命令，避免竞态条件

4. **阻塞操作**
   - 使用BLPOP/BRPOP实现高效的消息队列
   - 建议：合理设置超时时间，处理超时情况

5. **索引访问**
   - LINDEX性能较差，避免频繁使用
   - 建议：如果需要频繁随机访问，考虑使用Hash或Sorted Set

6. **错误处理**
   - 处理列表为空、元素不存在等情况
   - 建议：检查返回值，处理null和异常情况

7. **内存管理**
   - 定期清理过期数据，控制内存使用
   - 建议：结合TTL和LTRIM，实现自动清理
