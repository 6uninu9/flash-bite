package com.smart.init;

import com.smart.constant.CacheKeyConstants;
import com.smart.entity.Coupon;
import com.smart.enumeration.BloomFilterBizEnum;
import com.smart.mapper.CouponMapper;
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
 * 缓存预热初始化器
 * 项目启动时自动执行缓存预热：从数据库加载数据，同步至Redis或布隆过滤器
 * <p>
 * 设计说明：
 * 实现 CommandLineRunner 接口，在Spring Bean初始化完成、应用启动前执行预热逻辑
 */
@Slf4j
@Component
// 实现CommandLineRunner接口，在所有 Bean 创建并初始化完成之后、应用完全启动之前执行。
public class CacheWarmupInitializer implements CommandLineRunner {

    private final BloomCacheService bloomCacheService;

    // 上下文对象，用于获取对应的服务实例
    private final ApplicationContext applicationContext;

    private final RedissonClient redissonClient;

    private final CouponMapper couponMapper;

    private final StringRedisTemplate stringRedisTemplate;

    public CacheWarmupInitializer(BloomCacheService bloomCacheService, ApplicationContext applicationContext, RedissonClient redissonClient, CouponMapper couponMapper, StringRedisTemplate stringRedisTemplate) {
        this.bloomCacheService = bloomCacheService;
        this.applicationContext = applicationContext;
        this.redissonClient = redissonClient;
        this.couponMapper = couponMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        warmupBloomFilterCache();
        warmupSeckkillCouponStockCache();
    }

    /**
     * 布隆过滤器缓存预热
     * 设计说明：
     * 1. 基于 BloomFilterBizEnum 枚举遍历所有业务场景的布隆过滤器，统一管理、扩展性强
     * 2. 注入 ApplicationContext 动态获取业务Bean，避免硬编码注入所有业务Service，降低耦合
     * 3. 基于 BloomFilterDataService 统一接口抽象，所有业务Service实现该接口并重写ID查询方法
     * 4. 通过面向接口编程，统一调用获取业务ID，实现代码复用与解耦
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

    /**
     * 秒杀优惠券库存缓存预热
     * 设计说明：
     * 1. 获取所有秒杀优惠券库存数据
     * 2. 批量缓存到Redis中
     * 3. 没有给缓存设置过期时间的原因在于：
     *    一方面是方便测试，
     *    另一方面是为优惠卷秒杀活动的库存预扣减打下缓存基础，抵抗活动开启的瞬时压力
     */
    public void warmupSeckkillCouponStockCache() {
        log.info("开始优惠卷活动库存缓存预热......");

        // 1. 从数据库中获取所有优惠卷活动库存数据
        Coupon c = Coupon.builder()
                .isSeckill(Coupon.IS_SECKILL_YES)
                .status(Coupon.STATUS_RUNNING)
                .build();
        List<Coupon> coupons = couponMapper.list(c);

        // 2. 批量缓存到Redis中
        coupons.forEach(coupon -> {
            // 使用字符串类型的原因是简单、高效、原子性强、兼容所有库存操作，完全满足菜品库存的业务场景。
            stringRedisTemplate.opsForValue().set(CacheKeyConstants.SECKILL_COUPON_STOCK_KEY + coupon.getId(), String.valueOf(coupon.getSurplusStock()));
        });

        log.info("优惠卷活动库存缓存预热完成......");
    }
}
