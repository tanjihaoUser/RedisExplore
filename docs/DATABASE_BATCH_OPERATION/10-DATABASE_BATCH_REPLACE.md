# 数据库批量替换（Batch Replace）

## 使用情况

**常用程度：⭐⭐（不常用）**

批量替换是 MySQL 特有的批量处理方式，适用于"插入或替换"的场景，但由于会删除旧记录再插入新记录，使用频率较低。

## 概述

批量替换是指使用 `REPLACE INTO` 语句批量插入数据，如果遇到唯一键冲突，则删除旧记录再插入新记录。这种语法可以实现"插入或替换"的语义，但会触发 DELETE 触发器，可能影响自增ID，因此不推荐使用。

## 生产场景示例

### 1. 批量替换配置数据（Batch Replace Config）

**场景描述：**
- 批量更新系统配置，如果不存在则插入，存在则替换
- 配置数据需要完全替换，不需要保留旧值
- 可以接受自增ID变化

**实现示例：**
```java
// 批量替换配置数据
public void batchReplaceConfigs(List<SystemConfig> configs) {
    if (configs.isEmpty()) {
        return;
    }
    
    configMapper.batchReplace(configs);
}

// MyBatis XML 实现
// <insert id="batchReplace">
//     REPLACE INTO system_config (config_key, config_value, description, update_time)
//     VALUES
//     <foreach collection="list" item="item" separator=",">
//         (#{item.configKey}, #{item.configValue}, #{item.description}, NOW())
//     </foreach>
// </insert>
```

**业界案例：**
- **配置中心**：批量更新系统配置
- **参数管理**：批量更新业务参数
- **特性开关**：批量更新功能开关

### 2. 批量替换字典数据（Batch Replace Dictionary）

**场景描述：**
- 批量更新数据字典，完全替换旧值
- 字典数据需要保持最新状态
- 可以接受自增ID变化

**实现示例：**
```java
// 批量替换字典数据
public void batchReplaceDictionary(List<Dictionary> dictionaries) {
    if (dictionaries.isEmpty()) {
        return;
    }
    
    dictionaryMapper.batchReplace(dictionaries);
}
```

**业界案例：**
- **字典系统**：批量更新数据字典
- **枚举管理**：批量更新枚举值
- **分类管理**：批量更新分类数据

### 3. 数据同步替换（Data Sync Replace）

**场景描述：**
- 从外部系统同步数据，完全替换旧数据
- 不需要保留历史记录
- 可以接受自增ID变化

**实现示例：**
```java
// 批量同步替换订单数据
public void batchSyncReplaceOrders(List<Order> orders) {
    if (orders.isEmpty()) {
        return;
    }
    
    orderMapper.batchReplace(orders);
}

// 使用示例：定时任务同步订单数据
@Scheduled(fixedDelay = 300000) // 每5分钟执行一次
public void syncOrdersFromExternalSystem() {
    // 1. 从外部系统获取订单数据
    List<Order> orders = externalOrderService.fetchOrders();
    
    // 2. 批量替换（完全替换旧数据）
    batchSyncReplaceOrders(orders);
}
```

**业界案例：**
- **数据同步**：从第三方系统同步数据
- **数据迁移**：数据迁移时完全替换旧数据
- **数据修复**：批量修复数据，完全替换

## MyBatis 实现

### XML 配置方式

```xml
<!-- 批量替换 -->
<insert id="batchReplace">
    REPLACE INTO system_config (config_key, config_value, description, update_time)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.configKey}, #{item.configValue}, #{item.description}, NOW())
    </foreach>
</insert>

<!-- 批量替换多个字段 -->
<insert id="batchReplaceComplex">
    REPLACE INTO user_detail (user_id, nickname, avatar, bio, update_time)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.userId}, #{item.nickname}, #{item.avatar}, #{item.bio}, NOW())
    </foreach>
</insert>
```

### 注解方式

```java
@Insert({
    "<script>",
    "REPLACE INTO system_config (config_key, config_value) VALUES",
    "<foreach collection='list' item='item' separator=','>",
    "(#{item.configKey}, #{item.configValue})",
    "</foreach>",
    "</script>"
})
int batchReplace(@Param("list") List<SystemConfig> configs);
```

## 优点

1. **简单直接**
   - 语法简单，易于理解
   - 不需要先查询再判断插入或更新
   - 一次 SQL 完成替换操作

2. **原子性保证**
   - 整个操作在一个事务中
   - 保证数据一致性

3. **性能中等**
   - 比先查询再插入/更新快
   - 但比 INSERT ... ON DUPLICATE KEY UPDATE 慢

## 缺点

1. **会触发 DELETE 触发器**
   - 替换时会先删除旧记录，触发 DELETE 触发器
   - 可能导致意外的副作用

2. **自增ID会变化**
   - 替换时删除旧记录，插入新记录
   - 自增ID会重新分配，可能导致ID不连续

3. **性能较差**
   - 需要先删除再插入，性能比 INSERT ... ON DUPLICATE KEY UPDATE 差
   - 删除操作会增加数据库负担

4. **数据丢失风险**
   - 替换时会删除旧记录，可能导致数据丢失
   - 无法恢复被替换的数据

5. **数据库特定**
   - MySQL 特有语法，不通用
   - PostgreSQL 不支持 REPLACE INTO

## 可能存在的问题及解决方案

### 问题 1：自增ID变化

**问题描述：**
- REPLACE INTO 会先删除旧记录再插入新记录
- 自增ID会重新分配，导致ID不连续
- 可能影响业务逻辑

**解决方案：**
1. **使用业务主键**
```java
// 使用业务主键，不依赖自增ID
public void batchReplaceConfigs(List<SystemConfig> configs) {
    // 使用 config_key 作为主键，不依赖自增ID
    configMapper.batchReplace(configs);
}
```

2. **使用 INSERT ... ON DUPLICATE KEY UPDATE**
```java
// 推荐使用 INSERT ... ON DUPLICATE KEY UPDATE，不会删除旧记录
public void batchReplaceConfigs(List<SystemConfig> configs) {
    configMapper.batchInsertOrUpdate(configs);
}
```

### 问题 2：DELETE 触发器副作用

**问题描述：**
- REPLACE INTO 会触发 DELETE 触发器
- 可能导致意外的副作用
- 影响其他业务逻辑

**解决方案：**
1. **避免使用 DELETE 触发器**
```sql
-- 避免在表上定义 DELETE 触发器
-- 如果必须使用，需要确保触发器逻辑正确
```

2. **使用 INSERT ... ON DUPLICATE KEY UPDATE**
```java
// 推荐使用 INSERT ... ON DUPLICATE KEY UPDATE，不会触发 DELETE 触发器
public void batchReplaceConfigs(List<SystemConfig> configs) {
    configMapper.batchInsertOrUpdate(configs);
}
```

### 问题 3：数据丢失风险

**问题描述：**
- REPLACE INTO 会删除旧记录
- 可能导致数据丢失
- 无法恢复被替换的数据

**解决方案：**
1. **备份数据**
```java
// 替换前备份数据
public void batchReplaceConfigsWithBackup(List<SystemConfig> configs) {
    // 1. 备份旧数据
    List<SystemConfig> oldConfigs = configMapper.selectByKeys(
        configs.stream().map(SystemConfig::getConfigKey).collect(Collectors.toList())
    );
    backupService.backup(oldConfigs);
    
    // 2. 批量替换
    configMapper.batchReplace(configs);
}
```

2. **使用软删除**
```java
// 使用软删除，保留历史记录
public void batchReplaceConfigsWithSoftDelete(List<SystemConfig> configs) {
    // 1. 软删除旧数据
    List<String> keys = configs.stream()
        .map(SystemConfig::getConfigKey)
        .collect(Collectors.toList());
    configMapper.batchSoftDelete(keys);
    
    // 2. 插入新数据
    configMapper.batchInsert(configs);
}
```

### 问题 4：性能问题

**问题描述：**
- REPLACE INTO 需要先删除再插入，性能较差
- 比 INSERT ... ON DUPLICATE KEY UPDATE 慢

**解决方案：**
1. **使用 INSERT ... ON DUPLICATE KEY UPDATE**
```java
// 推荐使用 INSERT ... ON DUPLICATE KEY UPDATE，性能更好
public void batchReplaceConfigs(List<SystemConfig> configs) {
    configMapper.batchInsertOrUpdate(configs);
}
```

2. **批量大小控制**
```java
// 控制批量大小，避免性能问题
private static final int BATCH_SIZE = 1000;

public void batchReplaceConfigs(List<SystemConfig> configs) {
    for (int i = 0; i < configs.size(); i += BATCH_SIZE) {
        int end = Math.min(i + BATCH_SIZE, configs.size());
        List<SystemConfig> batch = configs.subList(i, end);
        configMapper.batchReplace(batch);
    }
}
```

## 最佳实践

1. **不推荐使用**
   - REPLACE INTO 会触发 DELETE 触发器
   - 自增ID会变化
   - 性能较差
   - **推荐使用 INSERT ... ON DUPLICATE KEY UPDATE**

2. **如果必须使用**
   - 使用业务主键，不依赖自增ID
   - 避免在表上定义 DELETE 触发器
   - 替换前备份数据
   - 控制批量大小

3. **替代方案**
   - **INSERT ... ON DUPLICATE KEY UPDATE**：推荐使用
   - **INSERT IGNORE**：如果只需要插入，不需要更新
   - **先查询再插入/更新**：如果需要复杂逻辑

## 性能对比

| 操作方式 | 100条记录耗时 | 1000条记录耗时 | 性能 |
|---------|-------------|--------------|------|
| REPLACE INTO | 50-150ms | 500-1500ms | 中等 |
| INSERT ... ON DUPLICATE KEY UPDATE | 30-100ms | 300-1000ms | **更好** |
| 先查询再插入/更新 | 200-500ms | 2000-5000ms | 较差 |

## 与其他方案对比

### vs INSERT ... ON DUPLICATE KEY UPDATE

**REPLACE INTO：**
- 会删除旧记录再插入新记录
- 会触发 DELETE 触发器
- 自增ID会变化
- 性能较差

**INSERT ... ON DUPLICATE KEY UPDATE：**
- 只更新，不删除
- 不会触发 DELETE 触发器
- 自增ID不变
- 性能更好
- **推荐使用**

### vs INSERT IGNORE

**REPLACE INTO：**
- 遇到冲突时替换
- 会删除旧记录

**INSERT IGNORE：**
- 遇到冲突时忽略
- 不会删除旧记录
- 适合"插入或跳过"的场景

## 总结

批量替换（REPLACE INTO）是 MySQL 提供的批量处理方式，但**不推荐使用**，原因如下：

1. ❌ **会触发 DELETE 触发器**，可能导致意外的副作用
2. ❌ **自增ID会变化**，可能导致ID不连续
3. ❌ **性能较差**，需要先删除再插入
4. ❌ **数据丢失风险**，替换时会删除旧记录

**推荐替代方案：**
- ✅ **INSERT ... ON DUPLICATE KEY UPDATE**：推荐使用，性能更好，不会删除旧记录
- ✅ **INSERT IGNORE**：如果只需要插入，不需要更新
- ✅ **先查询再插入/更新**：如果需要复杂逻辑

**适用场景：**
- ⚠️ 极少场景需要使用 REPLACE INTO
- ⚠️ 只有在确实需要完全替换数据且可以接受副作用时才使用

**不适用场景：**
- ❌ 需要保留历史记录的场景
- ❌ 依赖自增ID的场景
- ❌ 有 DELETE 触发器的表
- ❌ 对性能要求高的场景

**关键建议：**
- **强烈推荐使用 INSERT ... ON DUPLICATE KEY UPDATE 替代 REPLACE INTO**
- 只有在特殊场景下才考虑使用 REPLACE INTO
- 使用前需要充分评估副作用和性能影响

