package com.smart.constant;

public class CacheTimeConstant {
    // 逻辑过期时间：30分钟（热点Key核心）
    public static final long LOGICAL_EXPIRE_SECONDS = 1800;
    // 物理TTL随机值上限：5分钟（避免缓存雪崩）
    public static final long RANDOM_TTL_SECONDS = 300;
    // 空值缓存物理TTL：5分钟
    public static final long NULL_TTL_SECONDS = 300;
    // 常规缓存过期TTL：30分钟
    public static final long COMMON_TTL_SECONDS = 1800;
}