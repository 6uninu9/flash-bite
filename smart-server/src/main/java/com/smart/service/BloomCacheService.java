package com.smart.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RBloomFilter;
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
     */
    public boolean contains(RBloomFilter<String> bloomFilter, String id) {
        return bloomFilter.contains(id);
    }
}
