package com.smart.config;

import com.smart.enumeration.BloomFilterBizEnum;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonBloomConfig {

    /**
     * 创建菜品分类ID布隆过滤器
     */
    @Bean("categoryBloomFilter")
    public RBloomFilter<String> goodsBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BloomFilterBizEnum.CATEGORY.getOldKey());
        bloomFilter.tryInit(1000L, 0.01); // 初始化布隆过滤器 1000个元素，误判率0.01
        return bloomFilter;
    }

    /**
     * 创建优惠券ID布隆过滤器
     */
    @Bean("couponBloomFilter")
    public RBloomFilter<String> couponBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BloomFilterBizEnum.COUPON.getOldKey());
        bloomFilter.tryInit(1000L, 0.01); // 初始化布隆过滤器 1000个元素，误判率0.01
        return bloomFilter;
    }

    //其他布隆过滤器....
}
