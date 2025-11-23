# Redis String 命令列表

本文档基于 `BoundUtil` 中的 String 相关方法，总结 Redis String 类型的常用命令、使用场景和示例代码。




## 1. 基础 KV 操作

### SET - 设置值

**Redis命令：** `SET key value [EX seconds|PX milliseconds] [NX|XX]`

**方法签名：**
```java
boundUtil.set(key, value);
boundUtil.set(key, value, timeout, timeUnit);
boundUtil.setEx(key, value, Duration ttl);
boundUtil.setNx(key, value);
boundUtil.setNx(key, value, Duration ttl);
```

**用途：** 设置/更新一个键的值，可选过期时间和「仅在不存在时设置」语义。

**参数说明：**
- `key`: 键名
- `value`: 任意对象（通过 RedisTemplate 的 Jackson 序列化器序列化为 JSON）
- `timeout` / `ttl`: 过期时间，为 0 或负数时表示不过期
- `timeUnit` / `Duration`: 时间单位

**行为说明：**
- `set(key, value)`: 等价于 `SET key value`，不过期
- `set(key, value, timeout, unit)`: 等价于 `SET key value EX seconds`（或 PX）
- `setEx(key, value, ttl)`: 语义上对应 `SETEX key seconds value`
- `setNx(key, value)`: 对应 `SET key value NX`，仅当 key 不存在时设置

**使用场景：**

1. **对象缓存（Cache-Aside）**
```java
// 写数据库
int rows = userMapper.update(user);
if (rows > 0) {
    // 更新缓存（默认 JSON 序列化）
    boundUtil.set("user:" + user.getId(), user, 10, TimeUnit.MINUTES);
}
```

2. **分布式锁（简单版）**
```java
Boolean locked = boundUtil.setNx("lock:order:" + orderId, "1", Duration.ofSeconds(30));
if (Boolean.TRUE.equals(locked)) {
    try {
        // 执行业务逻辑
    } finally {
        boundUtil.del("lock:order:" + orderId);
    }
}
```

3. **一次性令牌 / 验证码**
```java
// 写入验证码，5分钟过期
boundUtil.set("captcha:" + phone, code, 5, TimeUnit.MINUTES);

// 校验时读取
String cachedCode = boundUtil.get("captcha:" + phone, String.class);
```

4. **会话/配置缓存**
```java
// 缓存配置 JSON
boundUtil.set("config:site", configDto, 1, TimeUnit.HOURS);
```




### GET / MGET - 获取值 / 批量获取

**Redis命令：**
- `GET key`
- `MGET key [key ...]`

**方法签名：**
```java
boundUtil.get(key, Class<T> clazz);
boundUtil.mGet(keys, Class<T> clazz);
boundUtil.getString(key);
boundUtil.getInt(key);
boundUtil.getLong(key);
```

**用途：** 读取单个或多个键的值，并反序列化为指定类型。

**参数说明：**
- `key` / `keys`: 键名或键名集合
- `clazz`: 目标类型（如 `String.class`, `User.class`）

**返回值：**
- `get`: 单个对象，不存在时返回 `null`
- `mGet`: 与 `keys` 对应的列表；不存在的 key 对应位置为 `null`

**使用场景：**

1. **读取对象缓存**
```java
User user = boundUtil.get("user:" + userId, User.class);
if (user == null) {
    user = userMapper.selectById(userId);
    if (user != null) {
        boundUtil.set("user:" + userId, user, 10, TimeUnit.MINUTES);
    }
}
```

2. **批量读取 Feed 数据**
```java
List<String> keys = postIds.stream()
    .map(id -> "post:" + id)
    .toList();

List<Post> posts = boundUtil.mGet(keys, Post.class);
```

3. **读取简单类型**
```java
String token = boundUtil.getString("session:token:" + sessionId);
Integer retryCount = boundUtil.getInt("login:retry:" + userId);
Long pv = boundUtil.getLong("page:pv:" + pageId);
```




## 2. 计数与自增

### INCR / INCRBY - 整数自增

**Redis命令：**
- `INCR key`
- `INCRBY key increment`

**方法签名：**
```java
boundUtil.incr(key);
boundUtil.incrBy(key, delta);
```

**用途：** 对存储为整数的值执行自增操作。

**参数说明：**
- `key`: 键名
- `delta`: 增量（可以为负数，相当于递减）

**返回值：** 自增后的最新值。

**使用场景：**

1. **页面 PV 统计**
```java
Long pv = boundUtil.incr("page:pv:" + pageId);
```

2. **接口调用次数统计**
```java
Long count = boundUtil.incr("api:count:" + apiName);
```

3. **限流计数（配合过期时间）**
```java
String key = "ip:req:" + ip + ":" + LocalDate.now();
Long count = boundUtil.incr(key);
if (count == 1) {
    boundUtil.expire(key, 1, TimeUnit.DAYS);
}
```




### INCRBYFLOAT - 浮点数自增

**Redis命令：** `INCRBYFLOAT key increment`

**方法签名：**
```java
boundUtil.incrByFloat(key, delta);
```

**用途：** 对存储为浮点数的值执行自增操作。

**参数说明：**
- `key`: 键名
- `delta`: 浮点增量

**注意：**
- Redis 使用 IEEE 754 双精度浮点数，会有精度误差。
- **业界通用做法**：金额、价格等敏感数值采用「放大为整数」存储，如：
  - 金额 * 100 存为分（整数）
  - 使用 `INCRBY` 而不是 `INCRBYFLOAT`

**使用场景：**
```java
// 不推荐直接对金额使用 INCRBYFLOAT
Double score = boundUtil.incrByFloat("user:score:" + userId, 2.5);
```




### 整数存储法辅助方法（推荐用于金额等精度敏感场景）

**设计思路：**
- 将浮点数放大为整数存储，避免二进制浮点表示导致的精度问题。
- 例如：金额 12.34 元，乘以 100 存储为 1234 分。

**方法签名：**
```java
// 使用整数存储法进行浮点数增量
boundUtil.incrByFloatAsInteger(key, delta, scale);

// 获取使用整数存储法的浮点数值
boundUtil.getFloatAsInteger(key, scale);
```

**参数说明：**
- `delta`: 原始浮点增量（如金额增量 12.34）
- `scale`: 放大倍数（如 100、1000），必须 > 0

**行为说明：**
- 内部会将 `delta * scale` 四舍五入为 long，通过 `INCRBY` 更新 Redis 中的整数值。
- 读取时再除以 `scale` 还原为浮点数。

**使用场景（金额统计）：**
```java
// 每次增加 12.34 元，使用分作为单位存储
Double totalAmount = boundUtil.incrByFloatAsInteger("order:amount:total", 12.34, 100);

// 读取当前总金额
Double amount = boundUtil.getFloatAsInteger("order:amount:total", 100);
```




## 3. 批量写入

### MSET - 批量设置

**Redis命令：** `MSET key value [key value ...]`

**方法签名：**
```java
boundUtil.mSet(Map<String, T> data);
```

**用途：** 一次性写入多个键值对，减少网络往返。

**使用场景：**

1. **缓存预热**
```java
Map<String, User> cache = new HashMap<>();
for (User u : users) {
    cache.put("user:" + u.getId(), u);
}
boundUtil.mSet(cache);
```




## 4. 其他常用命令

### APPEND - 追加字符串

**Redis命令：** `APPEND key value`

**方法签名：**
```java
boundUtil.append(key, value);
```

**用途：** 在原有字符串末尾追加内容，适合日志、消息拼接等场景。

**返回值：** 追加后字符串的总长度。

**示例：**
```java
boundUtil.append("log:job:1", "task finished
");
```




### GETRANGE / SETRANGE - 子串读写

**Redis命令：**
- `GETRANGE key start end`
- `SETRANGE key offset value`

**方法签名：**
```java
boundUtil.getRange(key, start, end);
boundUtil.setRange(key, offset, value);
```

**用途：**
- `GETRANGE`：获取字符串指定区间的子串。
- `SETRANGE`：从指定偏移量开始覆盖部分内容。

**示例：**
```java
// 获取前10个字符
String prefix = boundUtil.getRange("log:job:1", 0, 9);

// 覆盖某个位置的内容
boundUtil.setRange("user:nickname:1", 0, "Alice");
```




### STRLEN - 获取字符串长度

**Redis命令：** `STRLEN key`

**方法签名：**
```java
boundUtil.strLen(key);
```

**用途：** 获取字符串长度，常用于日志体积、内容大小统计等。

**示例：**
```java
Long len = boundUtil.strLen("log:job:1");
```




### GETSET - 设置新值并返回旧值

**Redis命令：** `GETSET key value`

**方法签名：**
```java
boundUtil.getSet(key, newValue, Class<T> clazz);
```

**用途：** 原子性地设置新值，同时返回旧值，适用于重置计数器、实现简单版本号等场景。

**示例：**
```java
Long last = boundUtil.getSet("counter:daily", 0L, Long.class);
```




## 5. 便捷方法（类型封装）

**方法签名：**
```java
boundUtil.setString(key, value);
boundUtil.getString(key);

boundUtil.setInt(key, value);
boundUtil.getInt(key);

boundUtil.setLong(key, value);
boundUtil.getLong(key);

boundUtil.setDouble(key, value);
boundUtil.getDouble(key);
```

**用途：** 为常用的 `String`/`Integer`/`Long`/`Double` 类型提供简化调用。

**使用场景：**
- 计数器、开关、简单配置等。




## 6. 业界常见实践（String）

1. **对象缓存：Cache-Aside 模式**
   - 读：先读缓存，未命中再查数据库并写入缓存。
   - 写：先更新数据库，再删除/更新缓存。

2. **热点 Key 前缀分类**
   - 如：`user:`, `post:`, `config:`, `session:`，便于管理和监控。

3. **过期时间随机化**
   - 在基础 TTL 上增加随机偏移（如 ±20%），避免大量 Key 同时过期造成缓存雪崩。

4. **避免大对象频繁写入**
   - 大 JSON 对象频繁写入会带来网络和序列化开销，可适当拆分为多个 Key 或使用 Hash。

5. **计数统一用 String + INCR**
   - 避免在 Hash 里自增简单计数，保持模型简单清晰。 


