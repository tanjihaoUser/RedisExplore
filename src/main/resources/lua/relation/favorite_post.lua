-- favorite_post.lua
-- KEYS[1]: 用户收藏列表 key (user:favorite:{userId})
-- KEYS[2]: 帖子收藏列表 key (post:favorited_by:{postId})
-- KEYS[3]: 收藏计数 key (post:favorite_count:{postId})
-- ARGV[1]: userId (用户ID)
-- ARGV[2]: postId (帖子ID)
--
-- 原子性地执行：
-- 1. 检查是否已收藏
-- 2. 添加到用户收藏列表
-- 3. 添加到帖子收藏列表
-- 4. 更新收藏计数

-- 1. 检查是否已收藏
local isMember = redis.call('SISMEMBER', KEYS[1], ARGV[2])
if isMember == 1 then
    -- 已经收藏过了，返回0表示未添加
    return 0
end

-- 2. 添加到用户收藏列表
local added1 = redis.call('SADD', KEYS[1], ARGV[2])
-- 3. 添加到帖子收藏列表
local added2 = redis.call('SADD', KEYS[2], ARGV[1])
-- 4. 更新收藏计数
local count = redis.call('INCR', KEYS[3])

-- 返回实际添加的成员数量（通常是1）
return added1

