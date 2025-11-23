-- like_post.lua
-- KEYS[1]: 帖子点赞列表 key (post:like:{postId})
-- KEYS[2]: 用户点赞列表 key (user:like:{userId})
-- KEYS[3]: 点赞计数 key (post:like_count:{postId})
-- ARGV[1]: userId (用户ID)
-- ARGV[2]: postId (帖子ID)
--
-- 原子性地执行：
-- 1. 检查是否已点赞
-- 2. 添加到帖子点赞列表
-- 3. 添加到用户点赞列表
-- 4. 更新点赞计数

-- 1. 检查是否已点赞
local isMember = redis.call('SISMEMBER', KEYS[1], ARGV[1])
if isMember == 1 then
    -- 已经点赞过了，返回0表示未添加
    return 0
end

-- 2. 添加到帖子点赞列表
local added1 = redis.call('SADD', KEYS[1], ARGV[1])
-- 3. 添加到用户点赞列表
local added2 = redis.call('SADD', KEYS[2], ARGV[2])
-- 4. 更新点赞计数
local count = redis.call('INCR', KEYS[3])

-- 返回实际添加的成员数量（通常是1）
return added1

