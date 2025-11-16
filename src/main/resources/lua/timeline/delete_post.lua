-- delete_post.lua
-- KEYS[1]: post key (post:{postId})
-- KEYS[2]: user post list key (user:post:rel:{userId})
-- KEYS[3]: empty set key (user:post:empty:set)
-- ARGV[1]: postId
-- ARGV[2]: userId
-- ARGV[3]: empty set expire time (秒)
--
-- 原子性地执行：
-- 1. 删除Post对象缓存
-- 2. 从用户帖子列表中移除该帖子ID
-- 3. 如果用户帖子列表为空，删除列表key并添加到空用户Set中

-- 1. 删除Post对象缓存
local postDeleted = redis.call('DEL', KEYS[1])

-- 2. 从用户帖子列表中移除该帖子ID（移除所有匹配的元素）
local removedCount = redis.call('LREM', KEYS[2], 0, ARGV[1])

-- 3. 检查列表是否为空
local listSize = redis.call('LLEN', KEYS[2])
if listSize == 0 then
    -- 列表为空，删除列表key
    redis.call('DEL', KEYS[2])
    -- 添加到空用户Set中
    redis.call('SADD', KEYS[3], ARGV[2])
    -- 设置Set的过期时间
    redis.call('EXPIRE', KEYS[3], ARGV[3])
end

-- 返回移除的元素数量
return removedCount

