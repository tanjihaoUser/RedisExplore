-- unlike_post.lua
-- KEYS[1]: 帖子点赞列表 key (post:like:{postId})
-- KEYS[2]: 用户点赞列表 key (user:like:{userId})
-- KEYS[3]: 点赞计数 key (post:like_count:{postId})
-- ARGV[1]: userId (用户ID)
-- ARGV[2]: postId (帖子ID)
--
-- 原子性地执行：
-- 1. 从帖子点赞列表移除
-- 2. 从用户点赞列表移除
-- 3. 更新点赞计数

-- 1. 从帖子点赞列表移除
local removed1 = redis.call('SREM', KEYS[1], ARGV[1])
-- 2. 从用户点赞列表移除并更新计数
if removed1 > 0 then
    redis.call('SREM', KEYS[2], ARGV[2])
    -- 3. 更新点赞计数（减1）
    redis.call('DECR', KEYS[3])
end

-- 返回移除的成员数量
return removed1

