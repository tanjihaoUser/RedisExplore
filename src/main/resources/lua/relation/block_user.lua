-- block_user.lua
-- KEYS[1]: 黑名单 key (user:blacklist:{userId})
-- KEYS[2]: 被拉黑列表 key (user:blocked_by:{blockedUserId})
-- ARGV[1]: blockedUserId (被拉黑的用户ID)
-- ARGV[2]: userId (拉黑的用户ID)
--
-- 原子性地执行：
-- 1. 检查是否已拉黑
-- 2. 添加到黑名单
-- 3. 添加到被拉黑列表

-- 1. 检查是否已拉黑
local isMember = redis.call('SISMEMBER', KEYS[1], ARGV[1])
if isMember == 1 then
    -- 已经拉黑了，返回0表示未添加
    return 0
end

-- 2. 添加到黑名单
local added1 = redis.call('SADD', KEYS[1], ARGV[1])
-- 3. 添加到被拉黑列表
local added2 = redis.call('SADD', KEYS[2], ARGV[2])

-- 返回实际添加的成员数量（通常是1）
return added1

