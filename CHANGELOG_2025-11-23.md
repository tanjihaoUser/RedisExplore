## 2025-11-23 关系服务与批量处理文档更新

### 一、关系服务功能实现（Relation Service）

- **新增 `RelationController`：**
  - 实现完整的社交媒体关系管理功能：
    - **关注功能：** 关注/取消关注、检查关注状态、获取关注列表/粉丝列表、批量检查关注关系
    - **点赞功能：** 点赞/取消点赞、检查点赞状态、获取点赞列表、批量检查点赞关系
    - **收藏功能：** 收藏/取消收藏、检查收藏状态、获取收藏列表、批量检查收藏关系
    - **黑名单功能：** 拉黑/取消拉黑、检查拉黑状态、获取黑名单列表、批量检查拉黑关系
  - 使用 Redis Set 数据结构实现，支持高效的集合操作
  - 提供批量检查接口，避免 N+1 查询问题

- **新增 `RelationService` 及相关实现：**
  - `RelationServiceImpl`：实现关注、点赞、收藏、黑名单等核心业务逻辑
  - `RelationPersistenceService`：实现关系数据的批量持久化策略
  - `RelationPersistenceServiceImpl`：Write-Behind 批量写入实现
    - 支持定时触发和定量触发双重机制
    - 批量合并同一 key 的多次操作（去重优化）
    - 批量存在性检查，避免重复插入
    - 异步批量写入数据库，提高性能

- **新增关系数据实体和 Mapper：**
  - `PostFavorite`：帖子收藏关系实体
  - `PostFavoriteMapper`：收藏关系数据访问层
  - `PostLikeMapper`：优化点赞关系查询，新增批量存在性检查方法

- **新增关系 Lua 脚本：**
  - `follow.lua` / `unfollow.lua`：关注/取消关注原子操作
  - `like_post.lua` / `unlike_post.lua`：点赞/取消点赞原子操作
  - `favorite_post.lua` / `unfavorite_post.lua`：收藏/取消收藏原子操作
  - `block_user.lua` / `unblock_user.lua`：拉黑/取消拉黑原子操作
  - 所有脚本使用 Redis Set 数据结构，保证操作的原子性和一致性

### 二、缓存策略优化

- **`WriteStrategy` 接口优化：**
  - 统一使用 `MethodExecutor` 接口，支持更灵活的方法执行方式
  - 新增 `ProceedingJoinPointMethodExecutor`：基于 AOP 切点的执行器实现
  - 新增 `MethodExecutor`：通用方法执行器接口

- **各种写入策略改进：**
  - `CacheAsideStrategy`：优化缓存删除逻辑
  - `WriteThroughStrategy`：优化缓存更新逻辑
  - `IncrementalWriteStrategy`：优化增量更新逻辑，支持更灵活的字段合并
  - `MQWriteBehindStrategy`：优化消息队列写入逻辑
  - `SnapshotWriteStrategy`：优化快照写入逻辑

- **读取策略优化：**
  - `LazyLoadStrategy`：优化懒加载逻辑
  - `ScheduledRefreshStrategy`：优化定时刷新逻辑
  - `ReadStrategy`：统一读取策略接口

- **`AsyncSQLWrapper` 重构：**
  - 优化异步 SQL 执行逻辑
  - 改进异常处理和重试机制
  - 支持更灵活的方法执行方式

### 三、Lua 脚本配置优化

- **`LuaScriptConfig` 增强：**
  - 支持更灵活的脚本加载方式
  - 优化脚本缓存机制
  - 改进脚本执行性能

- **`RelationScripts` 新增：**
  - 集中管理关系相关的 Lua 脚本
  - 提供类型安全的脚本执行接口
  - 支持批量操作优化

- **`RateLimitScripts` 优化：**
  - 优化限流脚本加载逻辑
  - 改进脚本执行性能

- **`TimeLineScripts` 优化：**
  - 优化时间线脚本执行逻辑
  - 改进脚本参数处理

### 四、批量处理文档完善

- **新增数据库批量处理文档（按使用频率排序）：**
  1. **DATABASE_BATCH_LOAD.md** ⭐⭐⭐：MySQL LOAD DATA INFILE 批量加载
  2. **DATABASE_BATCH_TRANSACTION.md** ⭐⭐⭐：批量事务处理
  3. **DATABASE_BATCH_REPLACE.md** ⭐⭐：MySQL REPLACE INTO（不推荐）
  4. **DATABASE_BATCH_MERGE.md** ⭐⭐：Oracle MERGE 语句
  5. **DATABASE_BATCH_PROCESSING_INDEX.md**：批量处理文档索引

- **文档特点：**
  - 每个文档包含：使用情况标注、生产场景示例、业界案例、实现方式、优缺点分析、问题解决方案、最佳实践、性能对比
  - 按业界使用频率排序，方便快速查找
  - 提供快速选择指南和性能对比总结

- **文档结构重组：**
  - Redis 命令文档移动到 `docs/REDIS_COMMAND/` 目录
  - 批量处理文档移动到 `docs/DATABASE_BATCH_OPERATION/` 目录
  - 保持文档结构清晰，便于维护

### 五、其他优化

- **`RedisCacheAspect` 优化：**
  - 改进缓存切面逻辑
  - 优化缓存键生成策略
  - 改进缓存失效处理

- **`PostController` 优化：**
  - 优化帖子相关接口
  - 改进错误处理

- **限流器优化：**
  - `SlideWindow`：优化滑动窗口限流逻辑
  - `TokenBucket`：优化令牌桶限流逻辑

- **MyBatis Mapper 优化：**
  - `PostLikeMapper.xml`：新增批量存在性检查 SQL
  - 优化批量操作性能

### 六、文档清理

- **删除根目录下的旧文档：**
  - `REDIS_HASH_COMMANDS.md`
  - `REDIS_LIST_COMMANDS.md`
  - `REDIS_STRING_COMMANDS.md`
  - `list.md`
  - 这些文档已移动到 `docs/REDIS_COMMAND/` 目录，保持项目结构整洁

### 总结：主要改进点

1. **关系服务功能：** 实现了完整的社交媒体关系管理功能，支持关注、点赞、收藏、黑名单等核心功能，使用 Redis Set 数据结构保证高性能和原子性。

2. **批量持久化策略：** 实现了 Write-Behind 批量写入策略，支持定时触发和定量触发双重机制，显著提高写入性能。

3. **缓存策略优化：** 统一了方法执行接口，优化了各种读写策略，提高了代码的可维护性和扩展性。

4. **文档完善：** 新增了完整的数据库批量处理文档，按使用频率排序，包含生产场景示例和最佳实践，便于团队学习和使用。

5. **代码质量提升：** 优化了异常处理、重试机制、异步执行等关键逻辑，提高了系统的稳定性和性能。

