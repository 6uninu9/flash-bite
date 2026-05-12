package com.smart.constant;

public class CacheConstant {
    // 逻辑过期时间：1分钟（热点Key核心）
    public static final long LOGICAL_EXPIRE_SECONDS = 60;
    // 物理TTL随机值上限：5分钟（避免缓存雪崩）
    public static final long RANDOM_TTL_SECONDS = 300;
    // 空值缓存物理TTL：5分钟
    public static final long NULL_TTL_SECONDS = 300;
}