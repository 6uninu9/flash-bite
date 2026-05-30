package com.smart.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Redis 逻辑过期缓存包装对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 实际业务数据（如List<DishVO>）
    private Object data;
    // 逻辑过期时间戳（毫秒）
    private Long expireTime;
    // 最近一次访问时间戳（毫秒），用于清理僵尸数据
    private Long lastAccessTime;
    // 标记对象，表示让下一次请求异步查询数据库刷新缓存
    public static final Object UPDATING_MARKER = new Object();
}
