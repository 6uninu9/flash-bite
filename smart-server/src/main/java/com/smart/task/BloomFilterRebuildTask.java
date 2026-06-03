package com.smart.task;

import com.smart.enumeration.BloomFilterBizEnum;
import com.smart.service.BloomCacheService;
import com.smart.service.BloomFilterDataService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 布隆过滤器定期全量重建任务
 * 在某一时段将所有注册的布隆过滤器缓存全部重建
 */
@Component
@Slf4j
public class BloomFilterRebuildTask {

    private final RedissonClient redissonClient;

    private final BloomCacheService bloomCacheService;

    // 上下文对象，用于获取对应的服务实例
    private final ApplicationContext applicationContext;

    // 布隆过滤器重建分布式锁的key
    private static final String REBUILD_LOCK_KEY = "lock:bloom:filter:rebuild";

    public BloomFilterRebuildTask(RedissonClient redissonClient1, BloomCacheService bloomCacheService, ApplicationContext applicationContext) {
        this.redissonClient = redissonClient1;
        this.bloomCacheService = bloomCacheService;
        this.applicationContext = applicationContext;
    }

    /**
     * 定期全量重建布隆过滤器 每天凌晨3点重建
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledRebuild() {
        // 使用分布式锁，防止多实例重复执行
        // 1. 创建分布式锁
        RLock lock = redissonClient.getLock(REBUILD_LOCK_KEY);
        boolean locked = false;
        try {
            // 2. 尝试获取锁，等待5秒
            locked = lock.tryLock(5, TimeUnit.SECONDS);
            if (!locked) {
                log.info("获取重建锁失败，其他实例正在执行重建");
                return;
            }
            // 3. 遍历所有枚举，自动重建所有布隆过滤器
            for (BloomFilterBizEnum biz : BloomFilterBizEnum.values()) {
                try {
                    doRebuild(biz);
                } catch (Exception e) {
                    log.error("重建失败: {}", biz.name(), e);
                    throw e; // 向上抛出异常
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取重建锁被中断", e);
        } catch (Exception e) {
            log.error("重建布隆过滤器异常", e);
        } finally {
            // 4. 释放自己的锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 执行重建逻辑
     * 如果需要更强的可用性 可以采用双过滤器方案
     */
    private void doRebuild(BloomFilterBizEnum bizEnum) {
        try {
            log.info("开始全量重建布隆过滤器");

            // 1. 获取对应的业务Service实例
            BloomFilterDataService bean = applicationContext.getBean(bizEnum.getServiceName(), BloomFilterDataService.class);

            // 2. 获取数据库中的id
            List<String> existingIds = bean.getKey();
            if (existingIds == null || existingIds.isEmpty()) {
                // 2.1. 数据库数据为空
                log.info("数据库数据为空，终止重建{}缓存", bizEnum.name());
                return;
            }

            // 3. 创建新的布隆过滤器（使用枚举常量中临时名称bloom:xxx:id:new）
            RBloomFilter<String> newBloomFilter = redissonClient.getBloomFilter(
                    bizEnum.getNewKey()
            );

            // 4. 使用与原来过滤器相同的参数初始化
            // 因为不能采用覆盖的方式重建布隆过滤器 所以只能采用新建、改名的方式达到重建的目的
            // 4.1. 获取原来的布隆过滤器
            RBloomFilter<String> oldFilter = redissonClient.getBloomFilter(bizEnum.getOldKey());
            // 4.2. 初始化新的布隆过滤器
            newBloomFilter.tryInit(
                    oldFilter.getExpectedInsertions(), // 预计插入数量
                    oldFilter.getFalseProbability()    // 误判率
            );

            // 5. 批量添加到新的key到布隆过滤器
            bloomCacheService.batchAddToBloomFilter(newBloomFilter, existingIds);

            // 6. 删除旧的布隆过滤器
            oldFilter.delete();

            // 7. 重命名新的布隆过滤器（原子操作）
            // 注意：这里存在短暂的服务不可用，可以考虑双布隆过滤器方案
            RKeys keys = redissonClient.getKeys();
            keys.rename(bizEnum.getNewKey(), bizEnum.getOldKey()); // 将新的key重命名为旧的key

            log.info("重建布隆过滤器完成....");
        } catch (Exception e) {
            log.error("重建布隆过滤器异常", e);
            // 如果重建失败 删掉新建的布隆过滤器
            try {
                RBloomFilter<String> tempFilter = redissonClient.getBloomFilter(bizEnum.getNewKey());
                tempFilter.delete(); // 存在就删除 不存在也不报错
            } catch (Exception ex) {
                log.error("清理临时过滤器失败", ex);
            }
            throw new RuntimeException(e);
        }
    }
}
