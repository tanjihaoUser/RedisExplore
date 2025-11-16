--[[
    令牌桶算法，能均匀分配令牌，平滑限流
    持续访问时（普通情况下）限流为rate，极限情况下（有一段空闲期后突然高并发）限流为burst桶容量
    注意：需要确保token是整数，否则无法正确限流
    当前时间戳单位必须是毫秒，同时必须先乘速率，再除1000。否则精度不足。
--]]
local key        = KEYS[1]
local rate       = tonumber(ARGV[1])        -- 每秒生成令牌数，这里单位是秒
local burst      = tonumber(ARGV[2])        -- 桶容量和起始令牌数
local nowMillis  = tonumber(ARGV[3])        -- 当前时间戳（这里单位是毫秒）

-- 读取当前令牌桶状态
local bucket = redis.call('HMGET', key, 'tokens', 'last_refill_time')
local currentTokens = tonumber(bucket[1]) or burst
local lastRefillTime = tonumber(bucket[2]) or nowMillis

-- 计算应该补充的令牌数（基于时间差）
local timePassed = math.max(0, nowMillis - lastRefillTime)
local tokensToAdd = math.floor((timePassed * rate) / 1000)

-- 只有在有令牌补充时才更新时间戳。否则密集访问时每次重置补充时间，导致每次得到的时间差很短，无法补充令牌
if tokensToAdd > 0 then
    currentTokens = math.min(burst, currentTokens + tokensToAdd)
    lastRefillTime = nowMillis  -- 重置补充时间
end

-- 尝试获取令牌
local allowed = 0
if currentTokens >= 1 then
    allowed = 1
    currentTokens = currentTokens - 1
end

-- 更新令牌桶状态
redis.call('HMSET', key, 'tokens', currentTokens, 'last_refill_time', lastRefillTime)

-- 设置过期时间（桶空闲时自动清理）。单位是毫秒，必须设置
-- 作用：key过期后，下一个请求会重新初始化桶（又是10个令牌）
-- 设置错误导致：key过期→重新初始化→10个令牌→key过期...
local expireSeconds = math.ceil((burst / rate) * 1000) + 3000 -- 补满桶的时间 + 缓冲
redis.call('PEXPIRE', key, expireSeconds)

return allowed