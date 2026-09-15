package com.smart.listener.mq;

import com.alibaba.fastjson2.JSON;
import com.smart.data.HotRankEvent;
import com.smart.service.HotDishRankingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 热销榜事件补偿消费者
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "hotDishRankCompensationTopic",
        consumerGroup = "hot-dish-rank-compensation-consumer-group",
        consumeMode = ConsumeMode.CONCURRENTLY,
        consumeThreadNumber = 8
)
public class HotDishRankCompensationListener implements RocketMQListener<String> {

    private final HotDishRankingService hotDishRankingService;

    public HotDishRankCompensationListener(HotDishRankingService hotDishRankingService) {
        this.hotDishRankingService = hotDishRankingService;
    }

    @Override
    public void onMessage(String message) {
        HotRankEvent event;
        try {
            event = JSON.parseObject(message, HotRankEvent.class);
        } catch (Exception e) {
            // 非法消息无法通过重试恢复，记录后直接结束消费。
            log.error("热销榜补偿消息格式错误，message={}", message, e);
            return;
        }

        if (event == null || event.getEventId() == null || event.getDishId() == null
                || event.getCategoryId() == null || event.getScore() == null || event.getEventTime() == null
                || event.getDedupSeconds() == null || event.getDishId() <= 0 || event.getCategoryId() <= 0
                || event.getScore() <= 0 || event.getEventTime() <= 0 || event.getDedupSeconds() <= 0) {
            log.error("热销榜补偿消息字段不合法，message={}", message);
            return;
        }

        // 重放仍使用事件幂等ID，RocketMQ重复投递不会重复累计分数。
        hotDishRankingService.replay(event);
        log.info("热销榜补偿消息处理完成，eventId={}", event.getEventId());
    }
}
