package com.smart.config;

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
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("bloom:category:id");
        bloomFilter.tryInit(500000L, 0.01);
        return bloomFilter;
    }

    //其他布隆过滤器....
}
