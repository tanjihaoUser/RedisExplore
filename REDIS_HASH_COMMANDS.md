# Redis Hash 命令列表

本文档基于 `BoundUtil` 中的 Hash 相关方法，总结 Redis Hash 类型的常用命令、使用场景和示例代码，重点适用于：用户资料、会话、购物车等「可部分更新的对象」。




## 1. 基础写入

### HSET / HMSET - 写入字段

**Redis命令：**
- `HSET key field value [field value ...]`
- `HMSET key field value [field value ...]`（Redis 新版本中 HSET 已支持多字段，HMSET 标记为过时）

**方法签名：**
```java
boundUtil.hSet(key, field, value);
boundUtil.hSetAll(key, map);
boundUtil.hSetAll(key, hashMap, timeout, timeUnit);
boundUtil.hmset(key, map);   // 语义上等价于 HSET/HMSET
```

**用途：** 向 Hash 中写入一个或多个字段值，可选设置过期时间。

**参数说明：**
- `key`: Hash 键名，如 `session:abc123`、`user:1`
- `field`: 字段名，如 `username`、`email`
- `value`: 字段值（通过 Jackson 序列化/反序列化）
- `map`: 字段名到字段值的映射
- `timeout` / `timeUnit`: 可选过期时间

**使用场景：**

1. **用户资料（User Profile）**
```java
// 写入部分字段
boundUtil.hSet("user:1", "username", "alice");
boundUtil.hSet("user:1", "avatar", "https://img.example.com/1.png");

// 批量写入
Map<String, Object> profile = new HashMap<>();
profile.put("username", "alice");
profile.put("age", 25);
profile.put("city", "Shanghai");
boundUtil.hSetAll("user:1", profile);
```

2. **配置中心（Config）**
```java
Map<String, String> config = Map.of(
    "site_name", "MyApp",
    "theme", "dark"
);
boundUtil.hSetAll("config:site", config, 1, TimeUnit.HOURS);
```

3. **缓存预热（对象拆分为字段）**
```java
// 将数据库中的用户对象拆成多个字段写入 Hash
Map<String, Object> map = new HashMap<>();
map.put("id", user.getId());
map.put("username", user.getUsername());
map.put("email", user.getEmail());
boundUtil.hmset("user:" + user.getId(), map);
```




## 2. 读取字段

### HGET / HMGET / HGETALL - 读取字段

**Redis命令：**
- `HGET key field`
- `HMGET key field [field ...]`
- `HGETALL key`

**方法签名：**
```java
boundUtil.hGet(key, field, Class<V> clazz);
boundUtil.hmget(key, fields, Class<V> clazz);
boundUtil.hGetAll(key, Class<T> clazz);
```

**用途：**
- `hGet`: 读取单个字段
- `hmget`: 批量读取多个字段
- `hGetAll`: 将整个 Hash 映射为一个对象

**参数说明：**
- `key`: Hash 键名
- `field` / `fields`: 字段名或字段集合
- `clazz`: 返回对象类型（`V` 为字段值类型，`T` 为整体对象类型）

**行为说明：**
- `hGet`：字段不存在时返回 `null` 并打印 warn 日志
- `hGetAll`：Hash 不存在或为空时返回 `null`，方便区分「无数据」和「数据为空对象」
- `hGetAll` 内部会把 `Map<Object, Object>` 转成 `Map<String, Object>`，再通过 `hashMappingUtil` 映射为对象

**使用场景：**

1. **读取单个字段**
```java
String username = boundUtil.hGet("user:1", "username", String.class);
```

2. **批量读取部分字段**
```java
List<String> fields = List.of("username", "email", "avatar");
List<String> values = boundUtil.hmget("user:1", fields, String.class);
```

3. **还原整个对象**
```java
// 将 Hash 转为 UserProfile 对象
UserProfile profile = boundUtil.hGetAll("user:1", UserProfile.class);
```




## 3. 计数和数值操作

### HINCRBY / HINCRBYFLOAT - 字段自增

**Redis命令：**
- `HINCRBY key field increment`
- `HINCRBYFLOAT key field increment`

**方法签名：**
```java
boundUtil.hIncrBy(key, field, delta);
boundUtil.hIncrByFloat(key, field, delta);
```

**用途：** 对 Hash 中的数值字段执行自增操作。

**参数说明：**
- `key`: Hash 键名
- `field`: 字段名
- `delta`: 自增步长（整数或浮点数）

**业界常见做法：**
- 计数类字段（点赞数、评论数、库存）通常使用 **整数 + HINCRBY**
- 金额等精度敏感字段使用「放大为整数」策略，如金额 * 100 存为分，再用 HINCRBY

**使用场景：**

1. **点赞数统计**
```java
// 帖子点赞数存储在 Hash 中
Long newLikeCount = boundUtil.hIncrBy("post:stats:" + postId, "like_count", 1);
```

2. **商品库存扣减**
```java
Long stock = boundUtil.hIncrBy("product:stock", productId, -1L);
```

3. **浮点数增量（不推荐用于金额）**
```java
Double score = boundUtil.hIncrByFloat("user:score:" + userId, "score", 2.5);
```




### 整数存储法辅助方法（金融场景推荐）

**方法签名：**
```java
// Hash 字段的整数存储法增量
boundUtil.hIncrByFloatAsInteger(key, field, delta, scale);

// 获取 Hash 字段的整数存储法浮点值
boundUtil.hGetFloatAsInteger(key, field, scale);
```

**用途：** 对金额、价格等精度敏感字段使用「整数存储法」，避免浮点精度问题。

**参数说明：**
- `delta`: 原始浮点增量（如金额增量 12.34）
- `scale`: 放大倍数（如 100、1000），必须 > 0

**示例：**
```java
// 每次给用户增加 12.34 元积分，使用分存储
Double score = boundUtil.hIncrByFloatAsInteger("user:wallet:" + userId, "balance", 12.34, 100);

// 获取余额（元）
Double balance = boundUtil.hGetFloatAsInteger("user:wallet:" + userId, "balance", 100);
```




## 4. 删除 / 判断存在 / 长度

### HDEL - 删除字段

**Redis命令：** `HDEL key field [field ...]`

**方法签名：**
```java
boundUtil.hDel(key, fields...);
```

**用途：** 删除一个或多个字段。

**使用场景：**
```java
// 删除会话中的某个属性
boundUtil.hDel("session:" + sessionId, "tempCode");
```




### HEXISTS - 字段是否存在

**Redis命令：** `HEXISTS key field`

**方法签名：**
```java
boundUtil.hExists(key, field);
```

**用途：** 判断 Hash 中某个字段是否存在。

**使用场景：**
```java
Boolean exists = boundUtil.hExists("user:1", "email");
```




### HLEN - 字段数量

**Redis命令：** `HLEN key`

**方法签名：**
```java
boundUtil.hLen(key);
```

**用途：** 获取 Hash 中字段的数量。

**使用场景：**
```java
Long fieldCount = boundUtil.hLen("user:1");
```




## 5. 其他 Hash 操作

### HKEYS / HVALS / HGETALL（Map）- 字段名与字段值

**Redis命令：**
- `HKEYS key`
- `HVALS key`
- `HGETALL key`

**方法签名：**
```java
boundUtil.hKeys(key);
boundUtil.hVals(key, Class<V> clazz);
boundUtil.hEntries(key, Class<V> clazz);
```

**用途：**
- `hKeys`：获取所有字段名
- `hVals`：获取所有字段值
- `hEntries`：获取字段名到字段值的 Map

**使用场景：**
```java
Set<String> fields = boundUtil.hKeys("user:1");
List<String> values = boundUtil.hVals("user:1", String.class);
Map<String, Object> map = boundUtil.hEntries("user:1", Object.class);
```




## 6. 典型业务场景（Hash）

### 场景1：Session 存储（当前项目 `SessionController`）

- 使用 Hash 存储会话信息，例如：
  - `sessions:{sessionId}` -> Hash
  - 字段包括 `userId`, `createdAt`, `lastAccessTime`, `attributes` 等

**示例：**
```java
// 登录成功后写入 session
Map<String, Object> sessionData = new HashMap<>();
sessionData.put("userId", userId);
sessionData.put("createdAt", System.currentTimeMillis());
boundUtil.hSetAll("sessions:" + sessionId, sessionData);
boundUtil.expire("sessions:" + sessionId, 30, TimeUnit.MINUTES);

// 读取 session
Long uid = boundUtil.hGet("sessions:" + sessionId, "userId", Long.class);
```

### 场景2：用户资料（Profile）

```java
// 写入部分资料
boundUtil.hSet("user:1", "nickname", "Alice");
boundUtil.hSet("user:1", "avatar", "https://img.example.com/1.png");

// 读取全部资料
UserProfile profile = boundUtil.hGetAll("user:1", UserProfile.class);
```

### 场景3：购物车

- Key：`cart:{userId}`
- Field：`productId`
- Value：数量/明细 JSON

```java
// 添加商品到购物车
boundUtil.hIncrBy("cart:" + userId, productId, 1);

// 获取购物车所有商品
Cart cart = boundUtil.hGetAll("cart:" + userId, Cart.class);
```

### 场景4：统计信息表（Post Stats）

- 把多个计数放在一个 Hash 中：
  - 字段：`like_count`, `comment_count`, `share_count` 等

```java
String key = "post:stats:" + postId;
boundUtil.hIncrBy(key, "like_count", 1);
boundUtil.hIncrBy(key, "comment_count", 1);
```




## 7. 业界常见实践（Hash）

1. **对象拆分 vs. 整体 JSON**
   - **String + JSON：** 适合读多写少、整体加载的对象（如帖子详情）。
   - **Hash：** 适合字段级更新、多计数字段（如用户资料、统计信息、Session）。

2. **避免大字段数量**
   - 单个 Hash 字段过多会影响运维（调试、迁移），建议按功能拆分多个 Hash。

3. **统一前缀管理**
   - 如：`user:*`, `session:*`, `cart:*`，便于监控和清理。

4. **过期时间控制**
   - 业务上不需要永久保存的 Hash（如 Session、购物车）要设置 TTL。

5. **与数据库的映射关系**
   - 常见做法：数据库为「权威数据源」，Hash 为缓存或加速结构：
     - 写：先写数据库，再删除或更新 Hash。
     - 读：先读 Hash，未命中再读数据库并回填。


