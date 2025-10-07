--[[
    漏桶算法，严格限流，平滑输出
    先处理（漏水），再接纳（注水）。
    处理速率最高时rate，不管是否有空档期
    这里只控制是否接纳请求，实际处理交给后端服务。按照速率均摊交给下游（对于Redis限流，返回1直接表示成功访问）
--]]
local key = KEYS[1]
local rate = tonumber(ARGV[1])        -- 处理速率（个/秒）
local capacity = tonumber(ARGV[2])    -- 桶容量（队列最大长度）
local nowMillis = tonumber(ARGV[3])   -- 当前时间戳（单位是毫秒）

-- 1. 初始化
local bucket = redis.call('HMGET', key, 'last_leak_time', 'water_level')
local lastLeakTime = tonumber(bucket[1]) or nowMillis -- 节奏器，控制处理速度绝对恒定
local waterLevel = tonumber(bucket[2]) or 0 -- 表示当前正在排队等待被处理的请求数量。类似队列管理器（队列当前长度）

-- 2. 计算漏出的水量，从上一次漏水到现在，应该漏掉多少水（处理多少请求）
--[[
        与令牌桶关键区别1：输出速率绝对恒定，无法加速。处理请求（漏水）的节奏由这里控制，只与时间有关
        令牌桶只要有剩余令牌就允许访问，这里根据处理速率和时间判断是否可以处理
--]]
local timePassed = math.max(0, nowMillis - lastLeakTime)
local leakedAmount = math.floor((timePassed * rate) / 1000)

-- 3. 更新水位，漏掉已处理的水和之前空闲期累积的多余处理能力。只依赖于时间流逝，与请求到达无关。
-- 实际的请求处理由下游服务完成，这里只根据时间推算"应该已经处理了多少个请求"
if leakedAmount > 0 then
    --[[
        与令牌桶关键区别2：没有储存处理能力
            令牌桶只根据时间判断是否可以新增令牌，如果有空闲期，令牌可能激增
            这里不管是否空闲，水位都不断下降，比如速率=1个/秒，容量=5，10s没有请求，11s时一个请求到达
            如果不与0比较取最大，waterLevel会变成-10，后续的判断waterLevel < capacity会多放过请求
            而如下代码waterLevel 被设为 max(0, waterLevel - 10) = 0。多出来的处理能力（10个请求）被丢弃了，并没有储存起来！
            对于忙碌期，比如速率=1个/秒，当前水位=2，经过3秒空档期，leakedAmount = 3
            水位变为 max(0, 2-3) = 0。表示已经处理完2个请求，甚至还多出来一个请求的处理能力，被忽略了
    --]]

    waterLevel = math.max(0, waterLevel - leakedAmount) -- 请求被处理，水位下降
    --[[
        精确计算剩余时间，避免累积误差。
        得到的时间对比
        lastLeakTime = nowMillis    0   510  1020  1535  2050
        使用如下代码得到的结果：        0   500  1000  1500  2000
        误差从0依次上升到50，可能引起不必要的限流
    --]]
    local leakInterval = 1000 / rate  -- 漏水的时间间隔（毫秒）
    local remainingTime = timePassed % leakInterval
    lastLeakTime = nowMillis - remainingTime -- 对齐到理论上的“漏水时刻”
end

-- 4. 判断新请求是否可以加入桶中（注水）。注意：这里不更新时间戳，保持漏水节奏
local allowed = 0
if waterLevel < capacity then
    waterLevel = waterLevel + 1 -- 新请求加入等待队列
    allowed = 1
else
    allowed = 0
end

-- 5. 更新状态
redis.call('HMSET', key, 'last_leak_time', lastLeakTime, 'water_level', waterLevel)

--[[
    设置过期时间（桶空闲时自动清理）。单位是毫秒，必须设置
    作用：防止无用的key长期占用内存
    设置错误会导致状态意外重置，影响限流准确性
--]]
local expireMs = math.ceil((capacity / rate) * 1000) + 3000  -- 处理完队列时间 + 缓冲
redis.call('PEXPIRE', key, expireMs)

return allowed