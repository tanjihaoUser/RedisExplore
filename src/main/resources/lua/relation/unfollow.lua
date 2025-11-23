-- unfollow.lua
-- KEYS[1]: 关注列表 key (user:follow:{followerId})
-- KEYS[2]: 粉丝列表 key (user:follower:{followedId})
-- ARGV[1]: followedId (被关注者ID)
-- ARGV[2]: followerId (关注者ID)
--
-- 原子性地执行：
-- 1. 从关注列表移除
-- 2. 从对方粉丝列表移除

-- 1. 从关注列表移除
local removed1 = redis.call('SREM', KEYS[1], ARGV[1])
-- 2. 从对方粉丝列表移除
if removed1 > 0 then
    redis.call('SREM', KEYS[2], ARGV[2])
end

-- 返回移除的成员数量
return removed1

