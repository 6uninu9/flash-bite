package com.smart.init;

import com.smart.enumeration.BloomFilterBizEnum;
import com.smart.service.BloomCacheService;
import com.smart.service.BloomFilterDataService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 布隆过滤器缓存预热初始化器
 * 项目启动时自动执行缓存预热：从数据库加载业务ID，同步至Redis集合与布隆过滤器
 * <p>
 * 设计说明：
 * 1. 实现 CommandLineRunner 接口，在Spring Bean初始化完成、应用启动前执行预热逻辑
 * 2. 基于 BloomFilterBizEnum 枚举遍历所有业务场景的布隆过滤器，统一管理、扩展性强
 * 3. 注入 ApplicationContext 动态获取业务Bean，避免硬编码注入所有业务Service，降低耦合
 * 4. 基于 BloomFilterDataService 统一接口抽象，所有业务Service实现该接口并重写ID查询方法
 *    通过面向接口编程，统一调用获取业务ID，实现代码复用与解耦
 */
@Slf4j
@Component
// 实现CommandLineRunner接口，在所有 Bean 创建并初始化完成之后、应用完全启动之前执行。
public class CacheWarmupInitializer implements CommandLineRunner {

    private final BloomCacheService bloomCacheService;

    // 上下文对象，用于获取对应的服务实例
    private final ApplicationContext applicationContext;

    private final RedissonClient redissonClient;

    public CacheWarmupInitializer(BloomCacheService bloomCacheService, ApplicationContext applicationContext, StringRedisTemplate redisTemplate, RedissonClient redissonClient) {
        this.bloomCacheService = bloomCacheService;
        this.applicationContext = applicationContext;
        this.redissonClient = redissonClient;
    }

    @Override
    public void run(String... args) throws Exception {
        warmupBloomFilterCache();
    }

    /**
     * 布隆过滤器缓存预热
     */
    public void warmupBloomFilterCache() {
        log.info("开始布隆过滤器缓存预热......");

        for (BloomFilterBizEnum biz : BloomFilterBizEnum.values()) {
            try {
                // 1. 使用applicationContext获取对应的BloomFilterDataService实例，
                // 并通过向上转型让父类引用指向子类实例 使父类能够调用子类实现的方法
                BloomFilterDataService bean = applicationContext.getBean(biz.getServiceName(), BloomFilterDataService.class);
                // 2. 调用子类实现的getAllIds方法获取对应业务需要缓存的id集合
                List<String> allIds = bean.getKey();
                // 3. 将数据缓存到布隆过滤器中
                bloomCacheService.batchAddToBloomFilter(redissonClient.getBloomFilter(biz.getOldKey()), allIds, biz);
            } catch (Exception e) {
                log.error("预热{}失败", biz.name(), e);
            }
        }
        log.info("布隆过滤器缓存预热完成......");
    }
}
