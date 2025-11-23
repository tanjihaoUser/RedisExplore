-- follow.lua
-- KEYS[1]: 关注列表 key (user:follow:{followerId})
-- KEYS[2]: 粉丝列表 key (user:follower:{followedId})
-- ARGV[1]: followedId (被关注者ID)
-- ARGV[2]: followerId (关注者ID)
--
-- 原子性地执行：
-- 1. 检查是否已关注（防止重复关注）
-- 2. 添加到关注列表
-- 3. 添加到对方的粉丝列表

-- 1. 检查是否已关注
local isMember = redis.call('SISMEMBER', KEYS[1], ARGV[1])
if isMember == 1 then
    -- 已经关注过了，返回0表示未添加
    return 0
end

-- 2. 添加到关注列表
local added1 = redis.call('SADD', KEYS[1], ARGV[1])
-- 3. 添加到对方的粉丝列表
local added2 = redis.call('SADD', KEYS[2], ARGV[2])

-- 返回实际添加的成员数量（通常是1）
return added1

