## 2025-11-16 Redis 与时间线改造说明

### 一、Redis 基础配置与序列化

- **`RedisConfig` 调整：**
  - 使用 `Jackson2JsonRedisSerializer<Object>` 作为 `RedisTemplate` 的 value 序列化器，配合 `ObjectMapper` 统一 JSON 序列化配置：
    - 序列化所有字段、禁用默认类型信息、支持 `LocalDateTime`（`JavaTimeModule`）、禁用时间戳输出、忽略未知属性。
  - 新增 `redisObjectMapper` Bean，暴露与 Redis 相同配置的 `ObjectMapper`，用于在 Lua 场景下**手动序列化对象为 JSON 字符串**：
    - 业界常见做法：当通过 Lua 脚本写入 Redis 时，为避免 `RedisTemplate` 二次序列化导致多重转义，先在 Java 中将对象转为 `String`，再以 `ARGV` 参数形式传入脚本。



### 二、Lua 脚本管理与限流实现

- **抽象 `LuaScriptConfig`：**
  - 统一脚本加载逻辑，提供 `createScript(path, resultType)` 辅助方法用于创建 `DefaultRedisScript<T>` 实例。
  - 建议脚本按功能目录管理（如 `lua/rate_limit`、`lua/timeline`），并通过常量维护脚本名。
- **`RateLimitScripts` 组件化：**
  - 定义：
    - `SLIDE_WINDOW`（滑动窗口限流）
    - `TOKEN_BUCKET`（令牌桶限流）
  - 通过 `@Bean` 暴露 `DefaultRedisScript<Long>`，供限流器注入使用。
  - 业界常用模式：**配置类中注册 Lua 脚本 Bean**，业务代码只关心脚本功能，不关心加载细节。
- **限流实现升级：**
  - `SlideWindow` / `TokenBucket` 从原来的字符串脚本名调用，改为注入具体的 `DefaultRedisScript<Long>`：
    - 使用 `boundUtil.executeSpecificScript(script, keys, args...)` 执行，类型更安全、 IDE 友好。
  - `RateLimiter.formatter` 精简为 `yyyy-MM-dd HH:mm:ss`，日志时间格式更清晰。



### 三、时间线脚本与帖子服务（PostService）

- **`TimeLineScripts` 设计（时间线 Lua 管理）：**
  - 改为 `@Component` 并继承 `LuaScriptConfig`，集中管理时间线相关脚本：
    - `PUBLISH_POST`：发布帖子、写入对象缓存、维护用户帖子列表、超出上限时删除尾部旧帖子。
    - `DELETE_POST`：删除帖子缓存，从用户列表中移除 postId，当列表为空时维护「空用户集合」。
  - 采用**懒加载 + 缓存 Map** 的方式，只在应用启动时加载一次脚本并缓存为：
    - `cachedScripts: Map<String, DefaultRedisScript<?>>`
    - `cachedReturnTypes: Map<String, Class<?>>`
  - 提供通用执行方法：
    - `public <T> T executeScript(String scriptName, List<String> keys, Object... args)`
    - 内部使用 `StringRedisTemplate` 并将 `Object... args` 统一转为 `String[]`，避免对已序列化的 JSON 再次序列化。
  - 这是业界比较推荐的封装方式：**脚本集中管理 + 类型安全 + 一次加载多次执行**。

- **`PostServiceImpl` 时间线与缓存策略：**
  - **写入帖子：**
    - `insert(Post post)`：
      - 使用 MyBatis `useGeneratedKeys` 返回自增主键 `id`。
      - 使用 `redisObjectMapper` 将 `Post` 序列化为 JSON 字符串，作为 Lua 脚本参数，避免多重转义。
      - 如果用户在 `USER_POST_EMPTY_SET` 中（代表「该用户无帖子」），发布新帖时移除该标记。
      - 通过 `PUBLISH_POST` 脚本原子执行：
        1. `SET post:{id} = JSON`（带过期时间）
        2. `LPUSH user:post:rel:{userId} id`
        3. `LLEN + LTRIM` 保证列表长度不超过 `USER_POST_LIST_MAX_SIZE`（当前为 5，用于测试）。
      - 这是典型的**「对象缓存 + 关系缓存（帖子列表）」原子写入**模式。

  - **分页查询用户帖子：混合缓存策略**
    - `getUserPagedPosts(userId, page, pageSize)`：
      - 使用 `USER_POST_EMPTY_SET`（Set）标记无帖子用户，防止频繁打到 DB（防止缓存穿透）。
      - 只缓存最新 `USER_POST_LIST_MAX_SIZE` 条帖子 ID：
        - 若请求页落在缓存范围内（如 page 较小），从 Redis 列表 + 批量缓存 `getPostsByIds` 获取。
        - 超出缓存范围时，直接走 `PostMapper.selectByUserIdWithPagination` 数据库分页（LIMIT/OFFSET）。
      - 这是业界常见的**混合策略**：
        - 热数据（首页/前几页）走 Redis，低延迟；
        - 冷数据（历史页）走 DB，保证完整性；
        - 通过限制列表长度控制内存。

    - `getUserPagedPostsFromCache`：
      - 使用 `exists + LLEN + LRANGE` 防御性检查，考虑并发 LTRIM / DEL / LREM 等操作导致数据缺失。
      - `idList` 判空是为了在脚本或人工删除列表后安全返回空结果，避免 NPE。

    - `getUserPagedPostsFromDatabase`：
      - 新增 `selectByUserIdWithPagination(userId, offset, limit)`：
        - SQL 中使用 `LIMIT #{limit} OFFSET #{offset}`。
        - 这是 MyBatis 中最常见的**直写分页**方式（不依赖三方插件），简单直接。

  - **用户帖子关系初始化：**
    - `initializeUserPostRelation(userId)`：
      - DB 查询该用户所有帖子，按时间倒序取前 `USER_POST_LIST_MAX_SIZE` 条，写入 `user:post:rel:{userId}` 列表。
      - 对无帖用户写入 `USER_POST_EMPTY_SET` 防止反复查 DB。
      - 符合常见的**冷启动 / 首次访问缓存预热**实践。

  - **批量帖子详情查询：避免 N+1 查询**
    - `getPostsByIds(List<Long> postIds)`：
      - 先构造 `post:{id}` Key 列表，通过 `mGet` 批量从 Redis 获取。
      - 对未命中 ID 使用 `PostMapper.selectByIds` 批量查询 DB，并回填到 Redis。
      - 按传入 `postIds` 原始顺序返回结果，避免与 DB 排序耦合。
      - 这是缓存层常用的**批量查 + 批量回填**模式，有效避免 N+1。

  - **更新帖子：Cache-Aside 模式**
    - `update(Post post)`：
      - 先调用 `postMapper.update` 更新数据库。
      - 成功后直接通过 `boundUtil.set(POST_PREFIX + id, post, ttl)` 更新缓存。
      - 这是标准的 **Cache-Aside** 写策略（先 DB 后缓存）。

  - **删除帖子：Lua 脚本保持原子性**
    - `delete(userId, postId)`：
      - 先 DB 逻辑删除 `post.is_deleted = 1`。
      - 使用 `DELETE_POST` 脚本原子执行：
        1. `DEL post:{id}`
        2. `LREM user:post:rel:{userId} id`
        3. 若列表为空，则 `DEL` 列表并把 `userId` 写入 `USER_POST_EMPTY_SET`（加过期）。
      - 采用**懒加载策略**：删除后不主动补齐列表，待下次查询时由 DB 填充。
      - 这是时间线场景中常见的折中：删除路径保持简单，查询路径负责恢复一致性。



### 四、使用文档与关系建模

- 新增 `REDIS_LIST_COMMANDS.md`、`REDIS_STRING_COMMANDS.md`、`REDIS_HASH_COMMANDS.md`
  - 按命令逐一说明：
    - Redis 原始命令语法。
    - 在 `BoundUtil` 中的方法签名。
    - 参数和索引（含 `-1` 代表最后一个元素、`count` 的正负含义等）。
    - 典型使用场景与 Java 示例（时间线、消息队列、任务队列、撤销/重做等）。
  - 强调业界实践和使用场景



### 五、MyBatis 与缓存注解

- **`PostMapper` 与 XML：**
  - 定义完整的 CRUD 与统计接口。
  - 在 XML 中实现：
    - 分页查询（`LIMIT #{limit} OFFSET #{offset}`）。
    - 批量查询（`IN (ids...)`）。
    - `useGeneratedKeys="true" keyProperty="id"` 获取自增主键。

- **`@RedisCache` 注解说明：**
  - `key` 注释更新为：**SpEL 表达式，无需手动加冒号后缀**，实际解析时会自动追加分隔符。
  - `SpelExpressionParserUtil.generateCacheKey` 改为以 `:` 连接前缀、方法名和参数摘要，更符合 Redis 常用 key 风格。



### 六、其他改动与说明

- `PostLike` / `PostLikeMapper` / `FollowMapper` 等：
  - 为后续点赞、关注关系功能做准备，采用简单的行表 + Mapper 接口形式，是业界常见的「关系表 + Redis 计数/缓存」组合。
- `BoundUtil`：
  - 补充了 List/Set/ZSet/Hash 的高层封装方法，并在文档中给出标准使用方式。



### 总结：整体设计与业界实践对齐点

1. **缓存模式：** 读请求采用 Lazy Load + Cache-Aside；写请求采用「先 DB 后缓存」或「先 DB 后删除缓存」策略，是最常见也最稳妥的实现。
2. **时间线建模：** 「对象 Key + 关系 List」结构（`post:{id}` + `user:post:rel:{userId}`），配合 Lua 保证写入原子性，是社交类 App 中普遍采用的方案。
3. **限流方案：** 使用 Redis 计数与 Lua 脚本实现滑动窗口/令牌桶，在分布式环境中可靠且性能好，是业界标准实现之一。
4. **分页策略：** 热页走 Redis，冷页走 DB，采用 SQL LIMIT/OFFSET，避免全量加载再拆分，既简单又易于扩展为游标分页或 Scroll 模式。
5. **文档与工具类：** 通过 `REDIS_*_COMMANDS.md` 和 `BoundUtil` 封装，将 Redis 命令、业务场景和代码实践串起来，便于后续团队成员按统一规范使用 Redis。 

