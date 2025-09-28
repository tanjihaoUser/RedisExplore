local key        = KEYS[1]
local rate       = tonumber(ARGV[1])        -- 每秒生成速率
local burst      = tonumber(ARGV[2])        -- 桶容量
local nowMillis  = tonumber(ARGV[3])        -- 当前毫秒

-- 读当前桶，mget表示获取多个value
local bucket = redis.call('HMGET', key, 'tokens', 'last_ts')
local tokens = tonumber(bucket[1]) or burst
local last   = tonumber(bucket[2]) or nowMillis

-- 时间差（秒）
local deltaSec = (nowMillis - last) / 1000
local refill   = deltaSec * rate
tokens = math.min(burst, tokens + refill)

-- 申请 1 个
local allowed = 0
if tokens >= 1 then
    allowed = 1
    tokens = tokens - 1
end

-- 写回桶
redis.call('HMSET', key, 'tokens', tokens, 'last_ts', nowMillis)
redis.call('PEXPIRE', key, 1000)   -- 1 秒过期即可
return allowed