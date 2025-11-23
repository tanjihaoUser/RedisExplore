# 数据库批量处理文档索引

## 概述

本文档索引了所有数据库批量处理相关的文档，按照业界使用频率排序，方便快速查找和学习。

## 文档列表（按使用频率排序）

### ⭐⭐⭐⭐⭐ 最常用

1. **[批量插入（Batch Insert）](./DATABASE_BATCH_INSERT.md)**
   - **使用频率：⭐⭐⭐⭐⭐（最常用）**
   - **适用场景：** 日志记录、关系数据批量写入、数据导入/迁移、订单批量创建
   - **性能提升：** 10-100倍
   - **数据库支持：** 所有数据库通用

2. **[批量查询（Batch Query）](./DATABASE_BATCH_QUERY.md)**
   - **使用频率：⭐⭐⭐⭐⭐（非常常用）**
   - **适用场景：** 批量存在性检查、批量获取用户信息、批量权限检查、解决N+1查询问题
   - **性能提升：** 10-100倍
   - **数据库支持：** 所有数据库通用

### ⭐⭐⭐⭐ 常用

3. **[批量更新（Batch Update）](./DATABASE_BATCH_UPDATE.md)**
   - **使用频率：⭐⭐⭐⭐（常用）**
   - **适用场景：** 批量状态更新、批量字段更新、批量计数器更新、批量时间戳更新
   - **性能提升：** 5-50倍
   - **数据库支持：** 所有数据库通用

4. **[批量删除（Batch Delete）](./DATABASE_BATCH_DELETE.md)**
   - **使用频率：⭐⭐⭐⭐（常用）**
   - **适用场景：** 批量物理删除、批量软删除、级联删除、条件批量删除
   - **性能提升：** 5-50倍
   - **数据库支持：** 所有数据库通用

5. **[写后批量处理（Write-Behind Batch）](./DATABASE_WRITE_BEHIND_BATCH.md)**
   - **使用频率：⭐⭐⭐⭐（常用）**
   - **适用场景：** 点赞关系批量写入、收藏关系批量写入、计数器批量写入、用户行为日志批量写入
   - **性能提升：** 20-100倍（吞吐量）
   - **数据库支持：** 所有数据库通用（应用层实现）

### ⭐⭐⭐ 较常用

6. **[批量同步（Batch Sync）](./DATABASE_BATCH_SYNC.md)**
   - **使用频率：⭐⭐⭐（较常用）**
   - **适用场景：** 缓存到数据库同步、数据库到数据库同步、数据恢复同步、增量同步
   - **性能提升：** 25-200倍
   - **数据库支持：** 所有数据库通用

7. **[INSERT ... ON DUPLICATE KEY UPDATE](./DATABASE_INSERT_ON_DUPLICATE_KEY_UPDATE.md)**
   - **使用频率：⭐⭐⭐（较常用）**
   - **适用场景：** 批量插入或更新、计数器批量更新、数据同步、配置信息批量更新
   - **性能提升：** 2-5倍（相比先查询再插入/更新）
   - **数据库支持：** MySQL 特有

8. **[批量加载（Batch Load）](./DATABASE_BATCH_LOAD.md)**
   - **使用频率：⭐⭐⭐（较常用）**
   - **适用场景：** 数据迁移、日志数据批量导入、数据初始化、数据同步
   - **性能提升：** 100-1000倍（相比普通INSERT）
   - **数据库支持：** MySQL 特有（LOAD DATA INFILE）

9. **[批量事务（Batch Transaction）](./DATABASE_BATCH_TRANSACTION.md)**
   - **使用频率：⭐⭐⭐（较常用）**
   - **适用场景：** 批量订单创建、批量数据迁移、批量状态更新、批量数据修复
   - **性能提升：** 减少事务开销
   - **数据库支持：** 所有数据库通用

### ⭐⭐ 不常用

10. **[批量合并（Batch Merge）](./DATABASE_BATCH_MERGE.md)**
    - **使用频率：⭐⭐（较常用，Oracle特有）**
    - **适用场景：** 批量插入或更新、数据仓库ETL、数据同步、增量更新
    - **性能提升：** 2-5倍（相比先查询再插入/更新）
    - **数据库支持：** Oracle 特有

11. **[批量替换（Batch Replace）](./DATABASE_BATCH_REPLACE.md)**
    - **使用频率：⭐⭐（不常用）**
    - **适用场景：** 批量替换配置数据、批量替换字典数据、数据同步替换
    - **性能提升：** 中等（不推荐使用）
    - **数据库支持：** MySQL 特有（REPLACE INTO）
    - **注意：** 不推荐使用，推荐使用 INSERT ... ON DUPLICATE KEY UPDATE

## 快速选择指南

### 根据使用场景选择

| 场景 | 推荐方案 | 文档链接 |
|------|---------|---------|
| 批量插入数据 | 批量插入 | [DATABASE_BATCH_INSERT.md](./DATABASE_BATCH_INSERT.md) |
| 批量查询数据 | 批量查询 | [DATABASE_BATCH_QUERY.md](./DATABASE_BATCH_QUERY.md) |
| 批量更新数据 | 批量更新 | [DATABASE_BATCH_UPDATE.md](./DATABASE_BATCH_UPDATE.md) |
| 批量删除数据 | 批量删除 | [DATABASE_BATCH_DELETE.md](./DATABASE_BATCH_DELETE.md) |
| 插入或更新（MySQL） | INSERT ... ON DUPLICATE KEY UPDATE | [DATABASE_INSERT_ON_DUPLICATE_KEY_UPDATE.md](./DATABASE_INSERT_ON_DUPLICATE_KEY_UPDATE.md) |
| 插入或更新（Oracle） | MERGE | [DATABASE_BATCH_MERGE.md](./DATABASE_BATCH_MERGE.md) |
| 高频写入场景 | Write-Behind 批量写入 | [DATABASE_WRITE_BEHIND_BATCH.md](./DATABASE_WRITE_BEHIND_BATCH.md) |
| 数据迁移/导入 | 批量加载（LOAD DATA INFILE） | [DATABASE_BATCH_LOAD.md](./DATABASE_BATCH_LOAD.md) |
| 需要事务保证 | 批量事务 | [DATABASE_BATCH_TRANSACTION.md](./DATABASE_BATCH_TRANSACTION.md) |
| 数据同步 | 批量同步 | [DATABASE_BATCH_SYNC.md](./DATABASE_BATCH_SYNC.md) |

### 根据数据库选择

| 数据库 | 推荐方案 | 文档链接 |
|--------|---------|---------|
| MySQL | INSERT ... ON DUPLICATE KEY UPDATE | [DATABASE_INSERT_ON_DUPLICATE_KEY_UPDATE.md](./DATABASE_INSERT_ON_DUPLICATE_KEY_UPDATE.md) |
| MySQL | LOAD DATA INFILE | [DATABASE_BATCH_LOAD.md](./DATABASE_BATCH_LOAD.md) |
| Oracle | MERGE | [DATABASE_BATCH_MERGE.md](./DATABASE_BATCH_MERGE.md) |
| PostgreSQL | ON CONFLICT DO UPDATE | 参考 INSERT ... ON DUPLICATE KEY UPDATE 文档 |
| 通用 | 批量插入/更新/查询/删除 | 参考对应的批量操作文档 |

## 性能对比总结

| 批量处理方式 | 性能提升 | 适用数据量 | 推荐度 |
|------------|---------|-----------|--------|
| 批量插入 | 10-100倍 | 100-10000条 | ⭐⭐⭐⭐⭐ |
| 批量查询 | 10-100倍 | 100-10000条 | ⭐⭐⭐⭐⭐ |
| 批量更新 | 5-50倍 | 100-10000条 | ⭐⭐⭐⭐ |
| 批量删除 | 5-50倍 | 100-10000条 | ⭐⭐⭐⭐ |
| Write-Behind 批量写入 | 20-100倍（吞吐量） | 高频写入 | ⭐⭐⭐⭐ |
| 批量同步 | 25-200倍 | 1000-100000条 | ⭐⭐⭐ |
| INSERT ... ON DUPLICATE KEY UPDATE | 2-5倍 | 100-1000条 | ⭐⭐⭐ |
| LOAD DATA INFILE | 100-1000倍 | 100000+条 | ⭐⭐⭐ |
| MERGE | 2-5倍 | 100-1000条 | ⭐⭐ |
| REPLACE INTO | 中等（不推荐） | 100-1000条 | ⭐（不推荐） |

## 最佳实践总结

### 1. 批量大小控制

- **小批量（< 100 条）：** 直接操作，性能差异不明显
- **中批量（100-1000 条）：** 推荐批量操作
- **大批量（> 1000 条）：** 分批处理，每批 500-2000 条

### 2. 数据库兼容性

- **MySQL：** 使用 INSERT ... ON DUPLICATE KEY UPDATE、LOAD DATA INFILE
- **Oracle：** 使用 MERGE 语句
- **PostgreSQL：** 使用 ON CONFLICT DO UPDATE
- **通用方案：** 使用批量插入/更新/查询/删除

### 3. 性能优化

- 控制批量大小，避免 SQL 过长
- 分批提交事务，避免长事务
- 合理使用索引
- 在非高峰期执行大批量操作

### 4. 错误处理

- 记录失败批次，支持重试
- 使用事务保证一致性
- 提供数据验证机制
- 监控批量操作性能

## 相关文档

- [批量写入时存在性检查策略](./BATCH_EXISTENCE_CHECK_STRATEGIES.md)
- [关系批量持久化策略](./RELATION_BATCH_PERSISTENCE.md)
- [关系持久化策略](./RELATION_PERSISTENCE_STRATEGY.md)

## 更新日志

- 2025-01-XX：创建文档索引，包含所有批量处理文档
- 按业界使用频率排序
- 提供快速选择指南和性能对比

