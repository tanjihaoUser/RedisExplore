-- unblock_user.lua
-- KEYS[1]: 黑名单 key (user:blacklist:{userId})
-- KEYS[2]: 被拉黑列表 key (user:blocked_by:{blockedUserId})
-- ARGV[1]: blockedUserId (被拉黑的用户ID)
-- ARGV[2]: userId (拉黑的用户ID)
--
-- 原子性地执行：
-- 1. 从黑名单移除
-- 2. 从被拉黑列表移除

-- 1. 从黑名单移除
local removed1 = redis.call('SREM', KEYS[1], ARGV[1])
-- 2. 从被拉黑列表移除
if removed1 > 0 then
    redis.call('SREM', KEYS[2], ARGV[2])
end

-- 返回移除的成员数量
return removed1

