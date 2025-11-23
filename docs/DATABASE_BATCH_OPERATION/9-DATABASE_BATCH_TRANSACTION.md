# 数据库批量事务（Batch Transaction）

## 使用情况

**常用程度：⭐⭐⭐（较常用）**

批量事务是数据库操作中常用的批量处理方式，适用于需要保证批量操作原子性的场景。

## 概述

批量事务是指将多个数据库操作放在同一个事务中执行，保证这些操作的原子性（要么全部成功，要么全部失败）。批量事务可以减少事务开销，提高性能，同时保证数据一致性。

## 生产场景示例

### 1. 批量订单创建（Batch Order Creation）

**场景描述：**
- 批量创建订单，需要保证订单主表和明细表的一致性
- 订单创建失败时，需要回滚所有相关操作
- 需要保证数据完整性

**实现示例：**
```java
// 批量创建订单（事务保证一致性）
@Transactional(rollbackFor = Exception.class)
public void batchCreateOrders(List<Order> orders) {
    try {
        // 1. 批量插入订单主表
        orderMapper.batchInsert(orders);
        
        // 2. 批量插入订单明细
        List<OrderItem> items = orders.stream()
            .flatMap(order -> order.getItems().stream()
                .map(item -> {
                    item.setOrderId(order.getId());
                    return item;
                }))
            .collect(Collectors.toList());
        orderItemMapper.batchInsert(items);
        
        // 3. 批量更新库存
        Map<Long, Integer> productIdToQuantity = items.stream()
            .collect(Collectors.groupingBy(
                OrderItem::getProductId,
                Collectors.summingInt(OrderItem::getQuantity)
            ));
        
        for (Map.Entry<Long, Integer> entry : productIdToQuantity.entrySet()) {
            productMapper.decreaseStock(entry.getKey(), entry.getValue());
        }
        
        log.info("Batch created {} orders", orders.size());
    } catch (Exception e) {
        log.error("Failed to batch create orders", e);
        throw new RuntimeException("批量创建订单失败", e);
    }
}
```

**业界案例：**
- **电商系统**：批量下单，保证订单和库存的一致性
- **金融系统**：批量交易，保证账户余额的一致性
- **物流系统**：批量发货，保证订单和物流信息的一致性

### 2. 批量数据迁移（Batch Data Migration）

**场景描述：**
- 数据迁移时需要保证数据一致性
- 迁移失败时需要回滚所有操作
- 需要支持断点续传

**实现示例：**
```java
// 批量迁移用户数据（事务保证一致性）
@Transactional(rollbackFor = Exception.class)
public void batchMigrateUsers(List<User> users) {
    try {
        // 1. 批量插入到目标表
        targetUserMapper.batchInsert(users);
        
        // 2. 批量更新源表状态
        List<Long> userIds = users.stream()
            .map(User::getId)
            .collect(Collectors.toList());
        sourceUserMapper.batchUpdateMigrationStatus(userIds, MigrationStatus.MIGRATED);
        
        // 3. 记录迁移日志
        List<MigrationLog> logs = users.stream()
            .map(user -> MigrationLog.builder()
                .userId(user.getId())
                .status(MigrationStatus.MIGRATED)
                .migratedAt(LocalDateTime.now())
                .build())
            .collect(Collectors.toList());
        migrationLogMapper.batchInsert(logs);
        
        log.info("Batch migrated {} users", users.size());
    } catch (Exception e) {
        log.error("Failed to batch migrate users", e);
        throw new RuntimeException("批量迁移用户数据失败", e);
    }
}
```

**业界案例：**
- **数据迁移**：系统迁移时批量迁移数据
- **数据同步**：主从数据库批量同步
- **数据备份**：批量备份数据

### 3. 批量状态更新（Batch Status Update）

**场景描述：**
- 批量更新订单状态，需要保证状态一致性
- 状态更新失败时需要回滚
- 需要记录状态变更日志

**实现示例：**
```java
// 批量更新订单状态（事务保证一致性）
@Transactional(rollbackFor = Exception.class)
public void batchUpdateOrderStatus(List<Long> orderIds, OrderStatus newStatus) {
    try {
        // 1. 查询当前订单状态
        List<Order> orders = orderMapper.selectByIds(orderIds);
        
        // 2. 验证状态转换是否合法
        for (Order order : orders) {
            if (!isValidStatusTransition(order.getStatus(), newStatus)) {
                throw new IllegalArgumentException(
                    "Invalid status transition from " + order.getStatus() + " to " + newStatus);
            }
        }
        
        // 3. 批量更新订单状态
        orderMapper.batchUpdateStatus(orderIds, newStatus);
        
        // 4. 批量插入状态变更日志
        List<OrderStatusLog> logs = orders.stream()
            .map(order -> OrderStatusLog.builder()
                .orderId(order.getId())
                .oldStatus(order.getStatus())
                .newStatus(newStatus)
                .changedAt(LocalDateTime.now())
                .build())
            .collect(Collectors.toList());
        orderStatusLogMapper.batchInsert(logs);
        
        log.info("Batch updated {} orders to status {}", orderIds.size(), newStatus);
    } catch (Exception e) {
        log.error("Failed to batch update order status", e);
        throw new RuntimeException("批量更新订单状态失败", e);
    }
}
```

**业界案例：**
- **订单系统**：批量更新订单支付状态、发货状态
- **内容审核系统**：批量审核内容，更新审核状态
- **工作流系统**：批量流转任务状态

### 4. 批量数据修复（Batch Data Repair）

**场景描述：**
- 数据不一致时需要批量修复
- 修复操作需要保证原子性
- 修复失败时需要回滚

**实现示例：**
```java
// 批量修复用户数据（事务保证一致性）
@Transactional(rollbackFor = Exception.class)
public void batchRepairUserData(List<Long> userIds) {
    try {
        // 1. 查询需要修复的用户
        List<User> users = userMapper.selectByIds(userIds);
        
        // 2. 批量修复用户数据
        List<User> repairedUsers = users.stream()
            .map(this::repairUserData)
            .collect(Collectors.toList());
        userMapper.batchUpdate(repairedUsers);
        
        // 3. 批量插入修复日志
        List<RepairLog> logs = repairedUsers.stream()
            .map(user -> RepairLog.builder()
                .userId(user.getId())
                .repairType("DATA_REPAIR")
                .repairedAt(LocalDateTime.now())
                .build())
            .collect(Collectors.toList());
        repairLogMapper.batchInsert(logs);
        
        log.info("Batch repaired {} users", userIds.size());
    } catch (Exception e) {
        log.error("Failed to batch repair user data", e);
        throw new RuntimeException("批量修复用户数据失败", e);
    }
}
```

**业界案例：**
- **数据修复**：批量修复数据不一致问题
- **数据补偿**：批量补偿丢失的数据
- **数据校正**：批量校正错误的数据

### 5. 批量级联操作（Batch Cascade Operation）

**场景描述：**
- 删除主表记录时，需要同时删除关联的子表记录
- 需要保证级联操作的原子性
- 删除失败时需要回滚

**实现示例：**
```java
// 批量级联删除帖子（事务保证一致性）
@Transactional(rollbackFor = Exception.class)
public void batchCascadeDeletePosts(List<Long> postIds) {
    try {
        // 1. 批量删除点赞记录
        postLikeMapper.batchDeleteByPostIds(postIds);
        
        // 2. 批量删除收藏记录
        postFavoriteMapper.batchDeleteByPostIds(postIds);
        
        // 3. 批量删除评论记录
        commentMapper.batchDeleteByPostIds(postIds);
        
        // 4. 批量删除帖子本身
        postMapper.batchDelete(postIds);
        
        log.info("Batch cascade deleted {} posts", postIds.size());
    } catch (Exception e) {
        log.error("Failed to batch cascade delete posts", e);
        throw new RuntimeException("批量级联删除帖子失败", e);
    }
}
```

**业界案例：**
- **内容平台**：删除帖子时级联删除所有关联数据
- **电商平台**：删除商品时级联删除库存、价格等数据
- **社交平台**：删除用户时级联删除用户的所有内容

## 实现方式

### 1. Spring 声明式事务

**特点：**
- 使用 `@Transactional` 注解
- 简单易用，代码清晰
- 推荐使用

**实现示例：**
```java
@Transactional(rollbackFor = Exception.class)
public void batchCreateOrders(List<Order> orders) {
    orderMapper.batchInsert(orders);
    orderItemMapper.batchInsert(items);
    productMapper.decreaseStock(productId, quantity);
}
```

### 2. 编程式事务

**特点：**
- 使用 `TransactionTemplate`
- 更灵活，可以精确控制事务边界
- 适合复杂事务逻辑

**实现示例：**
```java
public void batchCreateOrders(List<Order> orders) {
    transactionTemplate.execute(status -> {
        try {
            orderMapper.batchInsert(orders);
            orderItemMapper.batchInsert(items);
            productMapper.decreaseStock(productId, quantity);
            return null;
        } catch (Exception e) {
            status.setRollbackOnly();
            throw new RuntimeException("批量创建订单失败", e);
        }
    });
}
```

### 3. JDBC 事务

**特点：**
- 直接使用 JDBC 事务
- 最底层的事务控制
- 适合需要精细控制的场景

**实现示例：**
```java
public void batchCreateOrders(List<Order> orders) {
    try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);
        
        try {
            // 批量插入订单
            orderMapper.batchInsert(orders, conn);
            
            // 批量插入订单明细
            orderItemMapper.batchInsert(items, conn);
            
            // 更新库存
            productMapper.decreaseStock(productId, quantity, conn);
            
            conn.commit();
            log.info("Batch created {} orders", orders.size());
        } catch (SQLException e) {
            conn.rollback();
            log.error("Failed to batch create orders", e);
            throw new RuntimeException("批量创建订单失败", e);
        }
    } catch (SQLException e) {
        log.error("Failed to get connection", e);
        throw new RuntimeException("获取数据库连接失败", e);
    }
}
```

## 优点

1. **数据一致性**
   - 保证批量操作的原子性
   - 要么全部成功，要么全部失败
   - 避免部分成功导致的数据不一致

2. **性能优化**
   - 减少事务开销
   - 批量操作在同一个事务中
   - 减少数据库交互次数

3. **错误处理**
   - 失败时自动回滚
   - 保证数据完整性
   - 简化错误处理逻辑

4. **代码简洁**
   - 使用声明式事务，代码清晰
   - 不需要手动管理事务
   - 易于维护

## 缺点

1. **事务时间过长**
   - 大批量操作可能导致事务时间过长
   - 可能影响其他并发操作
   - 可能导致锁竞争

2. **内存消耗**
   - 需要一次性加载所有数据到内存
   - 大批量操作可能导致内存溢出

3. **回滚成本高**
   - 大批量操作失败时，回滚成本高
   - 可能影响数据库性能

4. **死锁风险**
   - 多个事务同时操作相同资源时可能死锁
   - 需要合理设计事务边界

## 可能存在的问题及解决方案

### 问题 1：事务时间过长

**问题描述：**
- 大批量操作可能导致事务时间过长
- 可能影响其他并发操作
- 可能导致锁竞争

**解决方案：**
1. **分批提交事务**
```java
// 分批提交事务，避免长事务
public void batchCreateOrders(List<Order> orders) {
    int batchSize = 100;
    
    for (int i = 0; i < orders.size(); i += batchSize) {
        int end = Math.min(i + batchSize, orders.size());
        List<Order> batch = orders.subList(i, end);
        
        // 每批单独事务
        transactionTemplate.execute(status -> {
            orderMapper.batchInsert(batch);
            orderItemMapper.batchInsert(getItems(batch));
            return null;
        });
    }
}
```

2. **异步处理**
```java
// 异步处理大批量操作
@Async
public CompletableFuture<Void> batchCreateOrdersAsync(List<Order> orders) {
    batchCreateOrders(orders);
    return CompletableFuture.completedFuture(null);
}
```

3. **优化批量大小**
```java
// 根据数据量动态调整批量大小
public void batchCreateOrders(List<Order> orders) {
    int batchSize = calculateOptimalBatchSize(orders.size());
    
    for (int i = 0; i < orders.size(); i += batchSize) {
        int end = Math.min(i + batchSize, orders.size());
        List<Order> batch = orders.subList(i, end);
        
        transactionTemplate.execute(status -> {
            orderMapper.batchInsert(batch);
            return null;
        });
    }
}

private int calculateOptimalBatchSize(int totalSize) {
    if (totalSize < 100) {
        return totalSize;
    } else if (totalSize < 1000) {
        return 100;
    } else {
        return 500;
    }
}
```

### 问题 2：死锁

**问题描述：**
- 多个事务同时操作相同资源时可能死锁
- 需要合理设计事务边界

**解决方案：**
1. **统一加锁顺序**
```java
// 统一按照 ID 排序，避免死锁
public void batchUpdateOrders(List<Long> orderIds) {
    // 排序，统一加锁顺序
    List<Long> sortedIds = orderIds.stream()
        .sorted()
        .collect(Collectors.toList());
    
    transactionTemplate.execute(status -> {
        orderMapper.batchUpdateStatus(sortedIds, newStatus);
        return null;
    });
}
```

2. **减少锁持有时间**
```java
// 减少锁持有时间，快速提交事务
@Transactional(rollbackFor = Exception.class)
public void batchUpdateOrders(List<Long> orderIds) {
    // 快速查询
    List<Order> orders = orderMapper.selectByIds(orderIds);
    
    // 快速更新
    orderMapper.batchUpdateStatus(orderIds, newStatus);
    
    // 异步记录日志，不阻塞事务
    asyncLogService.logOrderStatusChange(orders);
}
```

3. **使用乐观锁**
```java
// 使用版本号实现乐观锁，避免死锁
public void batchUpdateOrders(List<Order> orders) {
    transactionTemplate.execute(status -> {
        for (Order order : orders) {
            int updated = orderMapper.updateWithVersion(
                order.getId(), 
                order.getStatus(), 
                order.getVersion()
            );
            if (updated == 0) {
                throw new OptimisticLockingFailureException(
                    "Order " + order.getId() + " was modified by another transaction");
            }
        }
        return null;
    });
}
```

### 问题 3：内存溢出

**问题描述：**
- 大批量操作需要一次性加载所有数据到内存
- 可能导致内存溢出

**解决方案：**
1. **流式处理**
```java
// 使用流式处理，不一次性加载所有数据
public void batchProcessOrders(Stream<Order> orderStream) {
    List<Order> batch = new ArrayList<>();
    int batchSize = 100;
    
    orderStream.forEach(order -> {
        batch.add(order);
        if (batch.size() >= batchSize) {
            processBatch(new ArrayList<>(batch));
            batch.clear();
        }
    });
    
    // 处理剩余数据
    if (!batch.isEmpty()) {
        processBatch(batch);
    }
}

private void processBatch(List<Order> batch) {
    transactionTemplate.execute(status -> {
        orderMapper.batchInsert(batch);
        return null;
    });
}
```

2. **分批处理**
```java
// 分批处理，控制内存使用
public void batchCreateOrders(Long startId, Long endId) {
    int batchSize = 100;
    Long currentId = startId;
    
    while (currentId < endId) {
        // 分批查询
        List<Order> batch = orderMapper.selectBatch(currentId, batchSize);
        if (batch.isEmpty()) {
            break;
        }
        
        // 分批处理
        transactionTemplate.execute(status -> {
            orderMapper.batchInsert(batch);
            return null;
        });
        
        currentId = batch.get(batch.size() - 1).getId() + 1;
    }
}
```

### 问题 4：部分失败处理

**问题描述：**
- 批量操作中部分记录失败时，如何处理
- 是否需要全部回滚

**解决方案：**
1. **全部回滚（推荐）**
```java
// 全部回滚，保证数据一致性
@Transactional(rollbackFor = Exception.class)
public void batchCreateOrders(List<Order> orders) {
    try {
        orderMapper.batchInsert(orders);
        orderItemMapper.batchInsert(items);
    } catch (Exception e) {
        // 自动回滚整个事务
        log.error("Failed to batch create orders", e);
        throw e;
    }
}
```

2. **部分成功处理**
```java
// 部分成功处理，记录失败记录
public BatchResult batchCreateOrders(List<Order> orders) {
    List<Order> successOrders = new ArrayList<>();
    List<Order> failureOrders = new ArrayList<>();
    
    for (Order order : orders) {
        try {
            transactionTemplate.execute(status -> {
                orderMapper.insert(order);
                orderItemMapper.batchInsert(order.getItems());
                return null;
            });
            successOrders.add(order);
        } catch (Exception e) {
            log.error("Failed to create order {}", order.getId(), e);
            failureOrders.add(order);
        }
    }
    
    return BatchResult.builder()
        .successOrders(successOrders)
        .failureOrders(failureOrders)
        .build();
}
```

## 最佳实践

1. **事务边界设计**
   - 合理设计事务边界，避免长事务
   - 将非关键操作移出事务
   - 使用异步处理减少事务时间

2. **批量大小控制**
   - 根据数据量动态调整批量大小
   - 小批量（< 100 条）：单事务处理
   - 大批量（> 1000 条）：分批提交

3. **错误处理**
   - 使用声明式事务，自动回滚
   - 记录失败日志，支持重试
   - 提供数据验证机制

4. **性能优化**
   - 减少锁持有时间
   - 统一加锁顺序，避免死锁
   - 使用乐观锁减少锁竞争

5. **监控告警**
   - 监控事务耗时
   - 监控事务失败率
   - 监控死锁情况

## 性能对比

| 操作方式 | 100条记录耗时 | 1000条记录耗时 | 事务时间 |
|---------|-------------|--------------|---------|
| 逐条事务 | 500-1000ms | 5000-10000ms | 长 |
| 批量事务 | 50-200ms | 500-2000ms | 中等 |
| 分批事务 | 100-300ms | 1000-3000ms | 短 |

## 总结

批量事务是数据库操作中常用的批量处理方式，适用于：
- ✅ 批量订单创建
- ✅ 批量数据迁移
- ✅ 批量状态更新
- ✅ 批量数据修复
- ✅ 批量级联操作
- ✅ 任何需要保证批量操作原子性的场景

**关键要点：**
1. 保证批量操作的原子性
2. 合理设计事务边界，避免长事务
3. 控制批量大小，平衡性能和一致性
4. 处理错误情况，支持回滚和重试
5. 监控事务性能，及时优化

**适用场景：**
- ✅ 需要保证数据一致性的场景
- ✅ 批量操作需要原子性的场景
- ✅ 失败时需要全部回滚的场景

**不适用场景：**
- ❌ 可以接受部分成功的场景
- ❌ 大批量操作（需要分批处理）
- ❌ 对实时性要求极高的场景（考虑异步处理）

