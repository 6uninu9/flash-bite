package com.smart.listener.mq;

import com.alibaba.fastjson.JSONObject;
import com.smart.constant.CacheKeyConstants;
import com.smart.constant.CacheTimeConstant;
import com.smart.data.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 菜品逻辑过期 MQ 消息监听器
 * 用于菜品热缓存逻辑过期的设置失败的补偿重试
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = "cacheLogicalExpireTopic", // 订阅的主题
        consumerGroup = "dish-cache-logical-expire-consumer", // 消费者组
        consumeMode = ConsumeMode.CONCURRENTLY, // 消费模式 并发消费
        consumeThreadNumber = 32 // 并发消费线程数 处理器*2
)
public class DishCacheLogicalExpireListener implements RocketMQListener<String> {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String IDENTITY_KEY = "idempotent:dish:logicalExpire:";

    public DishCacheLogicalExpireListener(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void onMessage(String message) {
        // 1. 消息校验与转换
        if (message == null || message.trim().isEmpty()){
            log.error("消息为空，无法处理。");
            return;
        }
        log.info("收到菜品逻辑过期消息，菜品ID：{}", message);
        long categoryId;
        try {
            categoryId = Long.parseLong(message);
        } catch (NumberFormatException e) {
            log.error("消息为空或者消息体格式错误，非数字类型或空字符串，无法处理。消息：{}", message, e);
            return;
        }

        // 2. 幂等性校验，避免消息重复消费
        if (Boolean.FALSE.equals(stringRedisTemplate.opsForValue().setIfAbsent(IDENTITY_KEY + categoryId, "", CacheTimeConstant.DUPLICATE_CHECK_TTL_SECONDS, TimeUnit.SECONDS))) {
            log.warn("菜品热缓存重建消息重复");
            return;
        }

        try {
            String hotCategoryKey = CacheKeyConstants.HOT_CATEGORY_KEY_PREFIX + categoryId;

            // 3. 设置逻辑过期时间
            String redisDataJson = stringRedisTemplate.opsForValue().get(hotCategoryKey);
            if (redisDataJson != null) {
                // 缓存命中，将逻辑时间设置为过期，让下一次请求自动异步查询数据库刷新缓存
                RedisData redisData = JSONObject.parseObject(redisDataJson, RedisData.class);
                // 设置这个原因在于：
                // 为了避免热缓存的缓存穿透，将redisData中的data置为null，表示缓存穿透，如果查询到data为null，则说明缓存穿透，直接返回空集合
                // 所以在插入数据时，这里有可能查询到用于避免缓存穿透的空结果，所以为了不误以为是缓存穿透，进行异步刷新，就设置一个对象赋给data
                redisData.setData(RedisData.UPDATING_MARKER); // 标记为需要刷新，而不是误以为是缓存穿透
                redisData.setExpireTime(System.currentTimeMillis() - 1);
                stringRedisTemplate.opsForValue().set(hotCategoryKey, JSONObject.toJSONString(redisData));
            }
            // 缓存未命中，说明不是热点数据，不用设置
        } catch (Exception e) {
            log.error("菜品逻辑过期设置异常：{}", e.getMessage());
            // 移除幂等标识 进行补偿重试
            stringRedisTemplate.delete(IDENTITY_KEY + categoryId);
            throw new RuntimeException(e);
        }
    }
}
