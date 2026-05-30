package com.smart.listener.mq;

import com.smart.constant.CacheKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 菜品缓存删除 MQ 消息监听器
 * 用于菜品冷缓存删除失败的补偿重试
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = "dishCacheDeleteTopic", // 订阅的主题
        consumerGroup = "dish-cache-delete-consumer-group", // 消费者组
        consumeMode = ConsumeMode.CONCURRENTLY, // 消费模式 并发消费
        consumeThreadNumber = 32 // 并发消费线程数 处理器*2
)
public class DishColdCacheDeleteListener implements RocketMQListener<String> {

    private final StringRedisTemplate stringRedisTemplate;

    public DishColdCacheDeleteListener(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void onMessage(String id) {
        
        String coldKey = CacheKeyConstants.COLD_CATEGORY_KEY_PREFIX + id;

        stringRedisTemplate.delete(coldKey);
    }
}
