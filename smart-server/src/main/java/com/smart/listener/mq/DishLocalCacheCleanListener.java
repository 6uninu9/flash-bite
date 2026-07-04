package com.smart.listener.mq;

import com.github.benmanes.caffeine.cache.Cache;
import com.smart.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 监听本地缓存清理广播消息
 * 必须使用 BROADCASTING 模式，确保集群中每个节点都能收到消息并清理自己的 Caffeine 缓存
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = "dishLocalCacheCleanTopic",
        consumerGroup = "dish-local-cache-clean-group",
        messageModel = MessageModel.BROADCASTING // 广播模式
)
public class DishLocalCacheCleanListener implements RocketMQListener<Long> {

    private final Cache<String, List<DishVO>> hotDishLocalCache;

    public DishLocalCacheCleanListener(@Qualifier("hotDishLocalCache") Cache<String, List<DishVO>> hotDishLocalCache) {
        this.hotDishLocalCache = hotDishLocalCache;
    }

    @Override
    public void onMessage(Long categoryId) {
        // TODO 参数未校验
        log.info("接收到L1本地缓存清理广播，清理 categoryId:{}", categoryId);
        hotDishLocalCache.invalidate(String.valueOf(categoryId));
    }
}