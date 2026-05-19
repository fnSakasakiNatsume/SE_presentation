-- 秒杀资格原子预检（Redis 单线程执行，天然互斥）
-- ARGV[1] = voucherId, ARGV[2] = userId
-- 返回: 0=成功, 1=库存不足, 2=重复下单

local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

local stock = tonumber(redis.call('get', stockKey))
if stock == nil or stock <= 0 then
    return 1
end

if redis.call('sismember', orderKey, userId) == 1 then
    return 2
end

redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0
