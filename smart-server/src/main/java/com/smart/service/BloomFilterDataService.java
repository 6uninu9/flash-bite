package com.smart.service;

import java.util.List;

/**
 * 布隆过滤器数据服务接口
 */
public interface BloomFilterDataService {
    /**
     * 获取需要缓存进布隆过滤器的业务key（比如id）
     * @return 业务key列表
     */
    List<String> getKey();
}