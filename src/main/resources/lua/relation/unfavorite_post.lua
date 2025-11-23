-- unfavorite_post.lua
-- KEYS[1]: 用户收藏列表 key (user:favorite:{userId})
-- KEYS[2]: 帖子收藏列表 key (post:favorited_by:{postId})
-- KEYS[3]: 收藏计数 key (post:favorite_count:{postId})
-- ARGV[1]: userId (用户ID)
-- ARGV[2]: postId (帖子ID)
--
-- 原子性地执行：
-- 1. 从用户收藏列表移除
-- 2. 从帖子收藏列表移除
-- 3. 更新收藏计数

-- 1. 从用户收藏列表移除
local removed1 = redis.call('SREM', KEYS[1], ARGV[2])
-- 2. 从帖子收藏列表移除并更新计数
if removed1 > 0 then
    redis.call('SREM', KEYS[2], ARGV[1])
    -- 3. 更新收藏计数（减1）
    redis.call('DECR', KEYS[3])
end

-- 返回移除的成员数量
return removed1

