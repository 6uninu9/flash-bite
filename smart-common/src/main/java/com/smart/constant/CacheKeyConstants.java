package com.smart.constant;

/**
 * 缓存Key的名称常量类
 */
public class CacheKeyConstants {

    /**
     * 菜品缓存前缀
     */
    public static final String DISH_CACHE_KEY_PREFIX = "dish_";

    /**
     * 统计categoryId访问量的Key
     */
    public static final String CATEGORY_QPS_STATS_KEY = "stats:category:qps";

    /**
     * 标记热点分类的Key
     */
    public static final String HOT_CATEGORY_IDS_KEY = "hot:category:ids";

    /**
     * 冷分类缓存的Key前缀
     */
    public static final String COLD_CATEGORY_KEY_PREFIX = "cache:category:cold:";

    /**
     * 热分类缓存的Key前缀
     */
    public static final String HOT_CATEGORY_KEY_PREFIX = "cache:category:hot:";
}
