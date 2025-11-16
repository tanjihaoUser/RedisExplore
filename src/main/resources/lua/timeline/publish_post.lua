-- publish_post.lua
-- KEYS[1]: post key (post:{postId})
-- KEYS[2]: user post list key (user:post:rel:{userId})
-- ARGV[1]: post data (JSON字符串，已在Java代码中使用ObjectMapper序列化)
-- ARGV[2]: postId
-- ARGV[3]: post expire time (秒)
-- ARGV[4]: max list size (用户帖子列表上限)

-- 注意：ARGV[1] 是已经序列化好的 JSON 字符串，直接使用，不再序列化
-- 这样可以避免多重转义问题，同时保持操作的原子性

-- 1. 设置帖子缓存（直接使用已序列化的 JSON 字符串）
redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])

-- 2. 将帖子ID添加到用户帖子列表头部
redis.call('LPUSH', KEYS[2], ARGV[2])

-- 3. 检查列表大小，如果超过上限，删除尾部元素（最旧的帖子）
local listSize = redis.call('LLEN', KEYS[2])
local maxSize = tonumber(ARGV[4])
if listSize > maxSize then
    -- 保留前maxSize个元素，删除后面的元素
    -- LTRIM key start end: 保留索引从start到end的元素（包含两端）
    -- 索引从0开始，所以保留0到maxSize-1
    redis.call('LTRIM', KEYS[2], 0, maxSize - 1)
    local removedCount = listSize - maxSize
    return removedCount
end

-- 4. 返回成功（没有删除元素）
return 0
