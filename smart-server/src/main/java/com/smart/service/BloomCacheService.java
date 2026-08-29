package com.smart.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RBloomFilter;
import org.redisson.client.RedisException;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * 布隆过滤器缓存服务
 */
@Service
@Slf4j
public class BloomCacheService {

    // 以下统一使用String类型 后续拓展类型 可以转为泛型

    /**
     * 新增元素时，添加到布隆过滤器
     */
    public void addToBloomFilter(RBloomFilter<String> bloomFilter, String id) {
        try {
            bloomFilter.add(id);
        } catch (Exception e) {
            log.error("添加到布隆过滤器失败: {}", id, e);
        }
    }

    /**
     * 批量添加到布隆过滤器 全量重建时使用
     */
    public void batchAddToBloomFilter(RBloomFilter<String> bloomFilter, Collection<String> ids) {
        // 如果集合为空，则返回
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }

        try {
            // 批量添加到布隆过滤器
            for (String categoryId : ids) {
                bloomFilter.add(categoryId);
            }
        } catch (Exception e) {
            log.error("批量添加到布隆过滤器失败", e);
        }
    }

    /**
     * 判断某个元素是否存在于布隆过滤器中
     *
     * @return true 表示元素一定不存在（可安全拦截），false 表示可能存在
     */
    public boolean contains(RBloomFilter<String> bloomFilter, String id) {
        try {
            return !bloomFilter.contains(id);
        } catch (RedisException e) {
            // 兜底策略：布隆过滤器基于 Redis，宕机/超时/连接异常时无法判断，
            // 直接放行（视为"可能存在"），让请求继续走缓存与数据库降级链路，避免防穿透保护反噬可用性
            log.error("布隆过滤器查询失败（Redis不可用），放行查询，id：{}", id, e);
            return false;
        }
    }
}
