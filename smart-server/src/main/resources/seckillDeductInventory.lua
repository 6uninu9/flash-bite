---@diagnostic disable: undefined-global
---
--- Created by 24232.
--- DateTime: 2026/4/14 00:07
---

-- 库存键名（完整 Key，如 stock:dish:1001）
local stockKey = KEYS[1]
-- 需要扣减的数量（只有一个参数）
local count = tonumber(ARGV[1])

-- 获取当前库存
local cur = redis.call("get", stockKey)

-- 判断库存是否充足
if not cur or tonumber(cur) < count then
    return 0  -- 库存不足或不存在，返回失败
else
    -- 使用 decrby 扣减 String 类型的值
    redis.call("decrby", stockKey, count)
    return 1  -- 扣减成功
end
