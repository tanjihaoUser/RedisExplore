local key     = KEYS[1]
local limit   = tonumber(ARGV[1])
local window  = tonumber(ARGV[2])          -- 毫秒
local now     = tonumber(ARGV[3])
local member  = ARGV[4]                    -- 唯一标识

--[[
    使用zset进行窗口拦截，key是限流的业务key，zset的key，member是唯一标识，zset的元素key
    lua脚本的下标从1开始
--]]

-- 窗口起始时间
local windowStart = now - window
-- 1. 移除窗口外元素
redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)
-- 2. 统计当前窗口内访问次数
local cnt = redis.call('ZCOUNT', key, windowStart, now)
if cnt >= limit then
    return -1
end
-- 3. 记录本次访问
redis.call('ZADD', key, now, member)
-- 4. 设置过期
redis.call('PEXPIRE', key, window)
return 1