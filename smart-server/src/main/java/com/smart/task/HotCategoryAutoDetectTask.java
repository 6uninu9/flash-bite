package com.smart.task;

import com.smart.constant.CacheKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 热门分类自动检测器
 * 检测出一定时间内访问量超过一定阈值的热点数据，便于设置逻辑过期时间，提高缓存的命中率和避免缓存击穿
 */
@Slf4j
@Component
public class HotCategoryAutoDetectTask {

    private final StringRedisTemplate stringRedisTemplate;

    // 阈值：最近 30 秒内查询次数超过 50 视为热点
    private static final int HOT_THRESHOLD = 50;

    public HotCategoryAutoDetectTask(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 每30秒执行一次
    @Scheduled(cron = "0/30 * * * * ?")
    public void detectAndRefresh() {
        log.info("开始执行热点自动检测任务...");

        // 1. 获取访问量大于阈值的categoryId缓存
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                .opsForZSet()
                .reverseRangeByScoreWithScores(CacheKeyConstants.CATEGORY_QPS_STATS_KEY,
                        HOT_THRESHOLD,
                        Long.MAX_VALUE);

        // 2. 替换原本标记的热点缓存
        // 因为是单机部署所以没有考虑并发问题 如果是集群部署应该使用原子命令 分布式锁或lua脚本
        if (typedTuples != null && !typedTuples.isEmpty()) {
            // 2.2. 删除原本的缓存
            stringRedisTemplate.delete(CacheKeyConstants.HOT_CATEGORY_IDS_KEY);
            // 2.3. 添加新的缓存
            stringRedisTemplate.opsForSet().add(CacheKeyConstants.HOT_CATEGORY_IDS_KEY,
                    typedTuples.stream()
                            .map(ZSetOperations.TypedTuple::getValue).distinct().toArray(String[]::new));
        }

        // 3. 清空原本统计访问量的缓存
        stringRedisTemplate.delete(CacheKeyConstants.CATEGORY_QPS_STATS_KEY);

        log.info("热点自动检测任务执行完毕！");
    }
}
