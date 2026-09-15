---@diagnostic disable: undefined-global
-- ============================================================
-- 热销榜事件写入脚本（在 Redis 内原子执行）
--
-- 为什么要用一段 Lua：
--   一次事件需要"判重 + 在 4 个榜单 ZSet 上累加"共 5 步；若拆成多次命令，并发下可能
--   重复计分或部分写入。放进一段脚本后 Redis 保证整体原子性，且只需一次网络往返。
--
-- KEYS 参数（与 HotDishRankingServiceImpl.buildEventKeys 的返回顺序一一对应）：
--   KEYS[1] 事件幂等键  rank:dish:event:{eventId}
--   KEYS[2] 全店榜-小时切片 key（当前 5 分钟分片）
--   KEYS[3] 分类榜-小时切片 key
--   KEYS[4] 全店榜-日键
--   KEYS[5] 分类榜-日键
--
-- ARGV 参数（与 executeEvent 的实参顺序一一对应）：
--   ARGV[1] 菜品ID      -> 作为 4 个 ZSet 的 member
--   ARGV[2] 本次权重增量 -> 作为 ZINCRBY 的 score（浏览/加购/下单=1/3/10 或运营配置值）
--   ARGV[3] 幂等键TTL   -> 浏览事件=300s(滚动去重)；加购/下单=9 天
--   ARGV[4] 时键TTL     -> KEYS[2]/KEYS[3] 使用（覆盖小时榜窗口+冗余）
--   ARGV[5] 日键TTL     -> KEYS[4]/KEYS[5] 使用（9 天）
--
-- 幂等语义（关键）：
--   KEYS[1] 用 SET NX 抢锁：抢到(=1)才继续累加；抢不到说明该事件已被处理过
--   （正常重复/补偿重放/客户端超时后重试都算），直接返回 0 不累加。
--   因此"同一事件"绝不计两次；不同事件即使对应同一菜品也会各自累加一次。
-- ============================================================

-- 1. 幂等判断：事件键不存在才能创建并累加；已存在则说明事件重复，直接结束
if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[3]) == false then
    return 0
end

-- 2. 依次对 KEYS[2..5] 累加分数：member=菜品ID(ARGV[1])，score+=权重(ARGV[2])
for index = 2, #KEYS do
    redis.call('ZINCRBY', KEYS[index], ARGV[2], ARGV[1])
    -- 3. 设置各榜单 key 的过期时间：KEYS[2]/[3] 是小时切片用 ARGV[4]，
    --    KEYS[4]/[5] 是日键用 ARGV[5]；每次累加都会刷新 TTL（该 key 仍在被写入=仍然活跃）
    if index <= 3 then
        redis.call('EXPIRE', KEYS[index], ARGV[4])
    else
        redis.call('EXPIRE', KEYS[index], ARGV[5])
    end
end

-- 4. 全部累加成功才返回 1（调用方据此记录 metrics.recorded）
return 1
