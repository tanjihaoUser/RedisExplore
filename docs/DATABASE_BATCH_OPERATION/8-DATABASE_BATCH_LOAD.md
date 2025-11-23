# 数据库批量加载（Batch Load）

## 使用情况

**常用程度：⭐⭐⭐（较常用）**

批量加载是 MySQL 特有的高性能数据导入方式，适用于大批量数据导入场景，性能比普通 INSERT 快 10-100 倍。

## 概述

批量加载是指使用 `LOAD DATA INFILE` 语句从文件批量导入数据到数据库表。这是 MySQL 提供的最快的数据导入方式，适用于数据迁移、数据导入、批量初始化等场景。

## 生产场景示例

### 1. 数据迁移（Data Migration）

**场景描述：**
- 从其他系统迁移大量数据到 MySQL
- 一次性导入百万、千万级别的数据
- 需要快速完成数据迁移

**实现示例：**
```java
// 批量加载用户数据
public void batchLoadUsers(String csvFilePath) {
    String sql = "LOAD DATA INFILE ? " +
                 "INTO TABLE user_base " +
                 "FIELDS TERMINATED BY ',' " +
                 "ENCLOSED BY '\"' " +
                 "LINES TERMINATED BY '\\n' " +
                 "IGNORE 1 ROWS " +
                 "(id, username, email, phone, created_at)";
    
    try {
        jdbcTemplate.update(sql, csvFilePath);
        log.info("Batch loaded users from file: {}", csvFilePath);
    } catch (Exception e) {
        log.error("Failed to batch load users from file: {}", csvFilePath, e);
        throw new RuntimeException("批量加载用户数据失败", e);
    }
}
```

**业界案例：**
- **电商系统**：商品数据批量导入，一次性导入百万商品
- **金融系统**：客户数据批量迁移，从旧系统迁移到新系统
- **内容平台**：历史内容数据批量导入

### 2. 日志数据批量导入（Log Data Import）

**场景描述：**
- 从日志文件批量导入日志数据
- 日志量巨大，需要快速导入
- 定期批量导入日志到数据库

**实现示例：**
```java
// 批量加载日志数据
public void batchLoadLogs(String logFilePath) {
    String sql = "LOAD DATA INFILE ? " +
                 "INTO TABLE operation_log " +
                 "FIELDS TERMINATED BY '\\t' " +
                 "LINES TERMINATED BY '\\n' " +
                 "(user_id, operation_type, resource_id, created_at, details)";
    
    try {
        jdbcTemplate.update(sql, logFilePath);
        log.info("Batch loaded logs from file: {}", logFilePath);
    } catch (Exception e) {
        log.error("Failed to batch load logs from file: {}", logFilePath, e);
        throw new RuntimeException("批量加载日志数据失败", e);
    }
}

// 使用示例：定时任务批量导入日志
@Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
public void importDailyLogs() {
    String logFilePath = "/var/log/app/" + LocalDate.now().minusDays(1) + ".log";
    batchLoadLogs(logFilePath);
}
```

**业界案例：**
- **日志系统**：操作日志批量导入到数据库
- **监控系统**：监控数据批量导入
- **分析系统**：用户行为数据批量导入

### 3. 数据初始化（Data Initialization）

**场景描述：**
- 系统初始化时批量导入基础数据
- 配置数据、字典数据批量导入
- 测试数据批量导入

**实现示例：**
```java
// 批量加载配置数据
public void batchLoadConfigs(String csvFilePath) {
    String sql = "LOAD DATA INFILE ? " +
                 "INTO TABLE system_config " +
                 "FIELDS TERMINATED BY ',' " +
                 "ENCLOSED BY '\"' " +
                 "LINES TERMINATED BY '\\n' " +
                 "IGNORE 1 ROWS " +
                 "(config_key, config_value, description, created_at)";
    
    try {
        jdbcTemplate.update(sql, csvFilePath);
        log.info("Batch loaded configs from file: {}", csvFilePath);
    } catch (Exception e) {
        log.error("Failed to batch load configs from file: {}", csvFilePath, e);
        throw new RuntimeException("批量加载配置数据失败", e);
    }
}
```

**业界案例：**
- **配置中心**：系统配置数据批量初始化
- **字典系统**：数据字典批量导入
- **测试环境**：测试数据批量导入

### 4. 数据同步（Data Sync）

**场景描述：**
- 从外部系统同步数据
- 定期批量同步数据文件
- 数据仓库 ETL 过程

**实现示例：**
```java
// 批量同步订单数据
public void batchSyncOrders(String csvFilePath) {
    String sql = "LOAD DATA INFILE ? " +
                 "INTO TABLE orders " +
                 "FIELDS TERMINATED BY ',' " +
                 "ENCLOSED BY '\"' " +
                 "LINES TERMINATED BY '\\n' " +
                 "IGNORE 1 ROWS " +
                 "(order_id, user_id, product_id, amount, status, created_at)";
    
    try {
        jdbcTemplate.update(sql, csvFilePath);
        log.info("Batch synced orders from file: {}", csvFilePath);
    } catch (Exception e) {
        log.error("Failed to batch sync orders from file: {}", csvFilePath, e);
        throw new RuntimeException("批量同步订单数据失败", e);
    }
}

// 使用示例：定时任务从外部系统同步数据
@Scheduled(fixedDelay = 3600000) // 每小时执行一次
public void syncOrdersFromExternalSystem() {
    // 1. 从外部系统下载数据文件
    String csvFilePath = externalOrderService.downloadOrderFile();
    
    // 2. 批量加载到数据库
    batchSyncOrders(csvFilePath);
    
    // 3. 删除临时文件
    Files.deleteIfExists(Paths.get(csvFilePath));
}
```

**业界案例：**
- **电商系统**：从第三方系统同步订单数据
- **支付系统**：从支付网关同步交易数据
- **物流系统**：从物流公司同步物流信息

## MySQL 实现

### LOAD DATA INFILE 语法

```sql
LOAD DATA [LOW_PRIORITY | CONCURRENT] [LOCAL] INFILE 'file_path'
[REPLACE | IGNORE]
INTO TABLE table_name
[CHARACTER SET charset_name]
[FIELDS
    [TERMINATED BY 'string']
    [[OPTIONALLY] ENCLOSED BY 'char']
    [ESCAPED BY 'char']
]
[LINES
    [STARTING BY 'string']
    [TERMINATED BY 'string']
]
[IGNORE number LINES]
[(col_name_or_user_var, ...)]
[SET col_name = expr, ...]
```

### 基本用法

```sql
-- 从 CSV 文件导入数据
LOAD DATA INFILE '/path/to/users.csv'
INTO TABLE user_base
FIELDS TERMINATED BY ','
ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 ROWS
(id, username, email, phone, created_at);
```

### Java 实现

```java
// 使用 JDBC 执行 LOAD DATA INFILE
public void batchLoadUsers(String csvFilePath) {
    String sql = "LOAD DATA INFILE ? " +
                 "INTO TABLE user_base " +
                 "FIELDS TERMINATED BY ',' " +
                 "ENCLOSED BY '\"' " +
                 "LINES TERMINATED BY '\\n' " +
                 "IGNORE 1 ROWS " +
                 "(id, username, email, phone, created_at)";
    
    jdbcTemplate.update(sql, csvFilePath);
}

// 使用 LOCAL 关键字（从客户端读取文件）
public void batchLoadUsersLocal(String csvFilePath) {
    String sql = "LOAD DATA LOCAL INFILE ? " +
                 "INTO TABLE user_base " +
                 "FIELDS TERMINATED BY ',' " +
                 "ENCLOSED BY '\"' " +
                 "LINES TERMINATED BY '\\n' " +
                 "IGNORE 1 ROWS " +
                 "(id, username, email, phone, created_at)";
    
    jdbcTemplate.update(sql, csvFilePath);
}
```

### 生成 CSV 文件

```java
// 生成 CSV 文件用于批量加载
public void generateUsersCSV(List<User> users, String filePath) {
    try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath))) {
        // 写入表头
        writer.write("id,username,email,phone,created_at");
        writer.newLine();
        
        // 写入数据
        for (User user : users) {
            writer.write(String.format("%d,%s,%s,%s,%s",
                user.getId(),
                escapeCSV(user.getUsername()),
                escapeCSV(user.getEmail()),
                escapeCSV(user.getPhone()),
                user.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ));
            writer.newLine();
        }
        
        log.info("Generated CSV file: {} with {} users", filePath, users.size());
    } catch (IOException e) {
        log.error("Failed to generate CSV file: {}", filePath, e);
        throw new RuntimeException("生成 CSV 文件失败", e);
    }
}

private String escapeCSV(String value) {
    if (value == null) {
        return "";
    }
    // 如果包含逗号、引号或换行符，需要用引号包裹
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
}
```

## 优点

1. **性能极优**
   - 比普通 INSERT 快 10-100 倍
   - 直接读取文件，减少网络传输
   - 数据库内部优化，性能极高

2. **内存占用小**
   - 流式处理，不需要一次性加载所有数据到内存
   - 适合大批量数据导入

3. **事务效率高**
   - 可以关闭自动提交，批量提交事务
   - 减少事务开销

4. **灵活性强**
   - 支持多种文件格式（CSV、TSV等）
   - 支持字段映射和转换
   - 支持数据过滤和转换

## 缺点

1. **数据库特定**
   - MySQL 特有语法，不通用
   - PostgreSQL 使用 `COPY` 语句
   - Oracle 使用 `SQL*Loader`

2. **文件权限问题**
   - 需要文件系统访问权限
   - 服务器端文件路径限制
   - 安全风险（SQL注入）

3. **错误处理复杂**
   - 部分数据失败时，难以定位具体失败记录
   - 需要额外的错误处理机制

4. **数据格式要求**
   - 需要严格按照指定格式生成文件
   - 格式错误可能导致导入失败

## 可能存在的问题及解决方案

### 问题 1：文件权限问题

**问题描述：**
- `LOAD DATA INFILE` 需要服务器端文件访问权限
- 文件路径必须是服务器可访问的路径
- 安全风险（SQL注入）

**解决方案：**
1. **使用 LOCAL 关键字**
```java
// 使用 LOCAL 关键字，从客户端读取文件
public void batchLoadUsersLocal(String csvFilePath) {
    String sql = "LOAD DATA LOCAL INFILE ? " +
                 "INTO TABLE user_base " +
                 "FIELDS TERMINATED BY ',' " +
                 "ENCLOSED BY '\"' " +
                 "LINES TERMINATED BY '\\n' " +
                 "IGNORE 1 ROWS " +
                 "(id, username, email, phone, created_at)";
    
    jdbcTemplate.update(sql, csvFilePath);
}
```

2. **使用参数化查询**
```java
// 使用参数化查询，避免 SQL 注入
public void batchLoadUsers(String csvFilePath) {
    String sql = "LOAD DATA INFILE ? " +
                 "INTO TABLE user_base " +
                 "FIELDS TERMINATED BY ',' " +
                 "ENCLOSED BY '\"' " +
                 "LINES TERMINATED BY '\\n' " +
                 "IGNORE 1 ROWS " +
                 "(id, username, email, phone, created_at)";
    
    // 使用 PreparedStatement，避免 SQL 注入
    jdbcTemplate.update(sql, csvFilePath);
}
```

3. **文件路径验证**
```java
// 验证文件路径，防止路径遍历攻击
public void batchLoadUsers(String csvFilePath) {
    // 验证文件路径
    Path path = Paths.get(csvFilePath).normalize();
    if (!path.startsWith(Paths.get("/allowed/directory"))) {
        throw new IllegalArgumentException("Invalid file path");
    }
    
    String sql = "LOAD DATA INFILE ? " +
                 "INTO TABLE user_base " +
                 "FIELDS TERMINATED BY ',' " +
                 "ENCLOSED BY '\"' " +
                 "LINES TERMINATED BY '\\n' " +
                 "IGNORE 1 ROWS " +
                 "(id, username, email, phone, created_at)";
    
    jdbcTemplate.update(sql, csvFilePath);
}
```

### 问题 2：数据格式错误

**问题描述：**
- CSV 文件格式不正确可能导致导入失败
- 特殊字符处理不当
- 编码问题

**解决方案：**
1. **数据验证和清理**
```java
// 生成 CSV 文件前验证和清理数据
public void generateUsersCSV(List<User> users, String filePath) {
    try (BufferedWriter writer = Files.newBufferedWriter(
            Paths.get(filePath), 
            StandardCharsets.UTF_8)) {
        
        writer.write("id,username,email,phone,created_at");
        writer.newLine();
        
        for (User user : users) {
            // 验证和清理数据
            String username = sanitizeCSV(user.getUsername());
            String email = sanitizeCSV(user.getEmail());
            String phone = sanitizeCSV(user.getPhone());
            
            writer.write(String.format("%d,%s,%s,%s,%s",
                user.getId(),
                username,
                email,
                phone,
                user.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ));
            writer.newLine();
        }
    } catch (IOException e) {
        log.error("Failed to generate CSV file: {}", filePath, e);
        throw new RuntimeException("生成 CSV 文件失败", e);
    }
}

private String sanitizeCSV(String value) {
    if (value == null) {
        return "";
    }
    // 转义特殊字符
    value = value.replace("\"", "\"\"");
    // 如果包含逗号、引号或换行符，需要用引号包裹
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
        return "\"" + value + "\"";
    }
    return value;
}
```

2. **使用 IGNORE 关键字**
```sql
-- 忽略错误行，继续导入
LOAD DATA INFILE '/path/to/users.csv'
INTO TABLE user_base
FIELDS TERMINATED BY ','
ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 ROWS
(id, username, email, phone, created_at);
```

### 问题 3：大数据量导入

**问题描述：**
- 超大文件导入可能导致超时
- 内存占用问题
- 事务日志增长

**解决方案：**
1. **分批导入**
```java
// 分批导入大文件
public void batchLoadUsersLarge(String csvFilePath, int batchSize) {
    try (BufferedReader reader = Files.newBufferedReader(Paths.get(csvFilePath))) {
        List<String> batch = new ArrayList<>();
        String line;
        int lineNumber = 0;
        
        // 跳过表头
        reader.readLine();
        
        while ((line = reader.readLine()) != null) {
            batch.add(line);
            lineNumber++;
            
            if (batch.size() >= batchSize) {
                // 生成临时文件并导入
                String tempFile = generateTempFile(batch);
                loadBatchFromFile(tempFile);
                batch.clear();
                
                log.info("Loaded {} lines", lineNumber);
            }
        }
        
        // 处理剩余数据
        if (!batch.isEmpty()) {
            String tempFile = generateTempFile(batch);
            loadBatchFromFile(tempFile);
        }
    } catch (IOException e) {
        log.error("Failed to batch load large file: {}", csvFilePath, e);
        throw new RuntimeException("批量加载大文件失败", e);
    }
}
```

2. **关闭自动提交**
```java
// 关闭自动提交，批量提交事务
public void batchLoadUsersWithTransaction(String csvFilePath) {
    String sql = "LOAD DATA INFILE ? " +
                 "INTO TABLE user_base " +
                 "FIELDS TERMINATED BY ',' " +
                 "ENCLOSED BY '\"' " +
                 "LINES TERMINATED BY '\\n' " +
                 "IGNORE 1 ROWS " +
                 "(id, username, email, phone, created_at)";
    
    try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);
        
        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, csvFilePath);
            pstmt.executeUpdate();
            
            conn.commit();
            log.info("Batch loaded users from file: {}", csvFilePath);
        } catch (SQLException e) {
            conn.rollback();
            log.error("Failed to batch load users", e);
            throw new RuntimeException("批量加载用户数据失败", e);
        }
    } catch (SQLException e) {
        log.error("Failed to get connection", e);
        throw new RuntimeException("获取数据库连接失败", e);
    }
}
```

### 问题 4：性能优化

**问题描述：**
- 导入速度可能不够快
- 索引影响导入性能
- 锁竞争问题

**解决方案：**
1. **禁用索引**
```sql
-- 导入前禁用索引
ALTER TABLE user_base DISABLE KEYS;

-- 导入数据
LOAD DATA INFILE '/path/to/users.csv'
INTO TABLE user_base
FIELDS TERMINATED BY ','
ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 ROWS
(id, username, email, phone, created_at);

-- 导入后重新启用索引
ALTER TABLE user_base ENABLE KEYS;
```

2. **使用 LOW_PRIORITY**
```sql
-- 使用 LOW_PRIORITY，降低优先级，减少锁竞争
LOAD DATA LOW_PRIORITY INFILE '/path/to/users.csv'
INTO TABLE user_base
FIELDS TERMINATED BY ','
ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 ROWS
(id, username, email, phone, created_at);
```

3. **使用 CONCURRENT**
```sql
-- 使用 CONCURRENT，允许并发插入（MyISAM）
LOAD DATA CONCURRENT INFILE '/path/to/users.csv'
INTO TABLE user_base
FIELDS TERMINATED BY ','
ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 ROWS
(id, username, email, phone, created_at);
```

## 最佳实践

1. **文件格式规范**
   - 使用标准 CSV 格式
   - 正确处理特殊字符
   - 统一编码（UTF-8）

2. **数据验证**
   - 导入前验证数据格式
   - 清理和转义特殊字符
   - 验证数据完整性

3. **性能优化**
   - 大批量导入时禁用索引
   - 使用 LOW_PRIORITY 减少锁竞争
   - 分批导入超大文件

4. **错误处理**
   - 使用 IGNORE 关键字忽略错误行
   - 记录导入日志
   - 提供数据验证和修复机制

5. **安全考虑**
   - 使用参数化查询
   - 验证文件路径
   - 限制文件访问权限

## 性能对比

| 导入方式 | 100万条记录耗时 | 1000万条记录耗时 | 性能提升 |
|---------|--------------|---------------|---------|
| 逐条 INSERT | 10000-50000秒 | 100000-500000秒 | - |
| 批量 INSERT | 100-500秒 | 1000-5000秒 | 10-100倍 |
| LOAD DATA INFILE | 10-50秒 | 100-500秒 | **100-1000倍** |

## 与其他方案对比

### vs 批量 INSERT

**批量 INSERT：**
- 通用性好，所有数据库支持
- 性能中等
- 适合中小批量数据

**LOAD DATA INFILE：**
- MySQL 特有
- 性能极优
- 适合大批量数据导入

### vs PostgreSQL COPY

**PostgreSQL COPY：**
```sql
COPY user_base FROM '/path/to/users.csv' 
WITH (FORMAT csv, HEADER true);
```

**LOAD DATA INFILE：**
- MySQL 特有语法
- 功能类似，语法不同

## 总结

批量加载（LOAD DATA INFILE）是 MySQL 提供的高性能数据导入方式，适用于：
- ✅ 数据迁移
- ✅ 日志数据批量导入
- ✅ 数据初始化
- ✅ 数据同步
- ✅ 任何需要大批量数据导入的场景

**关键要点：**
1. 性能极优，比普通 INSERT 快 10-100 倍
2. MySQL 特有语法，不通用
3. 需要文件系统访问权限
4. 适合大批量数据导入（百万级以上）
5. 注意文件格式和安全问题

**适用场景：**
- ✅ MySQL 数据库
- ✅ 大批量数据导入（百万级以上）
- ✅ 数据迁移场景
- ✅ 对性能要求极高的场景

**不适用场景：**
- ❌ 非 MySQL 数据库（需要使用其他语法）
- ❌ 小批量数据导入（性能优势不明显）
- ❌ 需要实时导入的场景
- ❌ 文件系统访问受限的场景

