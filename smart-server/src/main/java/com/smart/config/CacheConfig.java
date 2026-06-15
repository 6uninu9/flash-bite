package com.smart.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smart.vo.DishVO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存配置类
 */
@Configuration
public class CacheConfig {

    /**
     * 热点菜品本地缓存 (L1)
     * 仅缓存热点分类数据，容量不需要太大，设置较短的物理过期时间作为兜底
     */
    @Bean("hotDishLocalCache")
    public Cache<String, List<DishVO>> hotDishLocalCache() {
        return Caffeine.newBuilder()
                // 最大缓存条目数，根据实际热点分类数量调整
                .maximumSize(200)
                // 写入后 30 秒物理过期（兜底策略，防止极端情况下 MQ 广播消息丢失导致脏数据常驻）
                .expireAfterWrite(30, TimeUnit.SECONDS)
                // 开启统计（可选，用于后续监控命中率）
                .recordStats()
                .build();
    }
}