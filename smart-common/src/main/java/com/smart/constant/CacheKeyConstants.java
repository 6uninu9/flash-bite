package com.smart.constant;

/**
 * 缓存Key的名称常量类
 */
public class CacheKeyConstants {

    /**
     * 统计categoryId访问量的Key
     */
    public static final String CATEGORY_QPS_STATS_KEY = "stats:category:qps";

    /**
     * 冷分类缓存的Key前缀
     */
    public static final String COLD_CATEGORY_KEY_PREFIX = "cache:category:cold:";

    /**
     * 热分类缓存的Key前缀
     */
    public static final String HOT_CATEGORY_KEY_PREFIX = "cache:category:hot:";

    /**
     * 秒杀优惠券库存Key前缀
     */
    public static final String SECKILL_COUPON_STOCK_KEY = "seckill:coupon:stock:";

    /**
     * 秒杀优惠券领取去重Key前缀
     */
    public static final String SECKILL_COUPON_TAKE_DEDUP_KEY_PREFIX = "dedup:seckill:coupon:";

    /**
     * 秒杀优惠券状态Key前缀
     */
    public static final String SECKILL_COUPON_STATUS_KEY = "seckill:coupon:status:";
}
