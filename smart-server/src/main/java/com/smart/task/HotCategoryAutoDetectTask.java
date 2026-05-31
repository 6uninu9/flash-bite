package com.smart.task;

import com.smart.constant.CacheKeyConstants;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 热门分类自动检测器
 * 检测出一定时间内访问量超过一定阈值的热点数据，便于设置逻辑过期时间，提高缓存的命中率和避免缓存击穿
 * 使用滑动窗口算法，对窗口内数据进行统计，并计算热点阈值，判断哪些分类是热点数据
 * 具体实现思路：
 * 1.将时间划分为连续的5秒小段（时间片），每个时间片独立存储一个ZSet。
 * 2.每5秒执行一次，将最近6个时间片（共30秒）的ZSet做一次求和合并（ZUNIONSTORE），
 * 得到前30秒内每个分类的总访问量，基于这个总访问量判断热点，
 * 3.窗口每执行一次向前滑动一个时间片自动加入最新的时间片，，丢弃最旧的时间片（删除其ZSet）。
 * 由于窗口内计算的访问量是重叠、连续、没有边界的，没有重置点，
 * 所以任何时候查询热点集合，得到的都是最近30秒内访问量超过阈值的分类，实时且平滑
 * 而每30秒执行一次清除访问量缓存的固定窗口，会把访问量切割成两段，
 * 比如在阈值为100的情况下，0~30秒访问量为90，30~60秒的前10秒访问了80次，后20秒访问10次，但实际上在30~40秒时就达到了90+80=170次，
 * 但是由于固定窗口的删除操作给切割了，损失了90次访问量的计算导致本该成为热点的分类被遗漏了
 */
@Slf4j
@Component
public class HotCategoryAutoDetectTask {

    private final StringRedisTemplate stringRedisTemplate;

    private static final int TIME_SLICE_SECONDS = 5;          // 时间片大小（秒）
    private static final int WINDOW_SIZE = 6;                 // 滑动窗口包含的时间片数
    private static final long ABSOLUTE_HOT_THRESHOLD = 10;  // 绝对热点阈值
    private static final double DYNAMIC_THRESHOLD_MULTIPLE = 3.0; // 动态阈值倍数
    private static final long TEMP_KEY_TTL_SECONDS = 30;      // 临时Key过期时间
    private static final long TIME_SLICE_KEY_TTL_SECONDS = TIME_SLICE_SECONDS * (WINDOW_SIZE + 3); // 时间片Key过期兜底

    public HotCategoryAutoDetectTask(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 每5秒执行一次
    @Scheduled(fixedDelay = TIME_SLICE_SECONDS * 1000)
    public void detectAndRefresh() {
        log.info("开始执行滑动窗口热点检测任务...");

        // 1. 获取上一个完整时间片的序号
        long nowSlice = System.currentTimeMillis() / 1000 / TIME_SLICE_SECONDS; // 当前时间片的序号
        long lastCompleteSlice = nowSlice - 1;   // 上一个完整时间片的序号

        // 2. 构建窗口内所有时间片的key
        // 共WINDOW_SIZE个，从 lastCompleteSlice 往前推，
        // 实现自动加入时间片，即当次的循环得到的窗口结果自动包含上一次的最新时间片和自动移除上次窗口最早的时间片
        List<String> windowSliceKeys = new ArrayList<>();
        for (int i = 0; i < WINDOW_SIZE; i++) {
            long slice = lastCompleteSlice - i;
            windowSliceKeys.add(getTimeSliceKey(slice));
        }

        // 3. 合并窗口内所有时间片的ZSet，存储到临时Key
        if (windowSliceKeys.isEmpty()) {
            log.info("获取不到时间片的key");
            return;
        }
        String firstKey = windowSliceKeys.getFirst();  // 取出合并的第一个key
        List<String> otherKeys = windowSliceKeys.subList(1, windowSliceKeys.size()); // 取出剩余合并的key
        String tempUnionKey = CacheKeyConstants.CATEGORY_QPS_STATS_KEY + ":union:" + lastCompleteSlice;
        stringRedisTemplate.opsForZSet()
                .unionAndStore(firstKey, otherKeys, tempUnionKey, Aggregate.SUM);

        // 4. 为临时Key设置TTL30秒兜底（防止残留）
        stringRedisTemplate.expire(tempUnionKey, TEMP_KEY_TTL_SECONDS, TimeUnit.SECONDS);

        // 5. 获取所有分类及其窗口总分（合并结果）
        Set<ZSetOperations.TypedTuple<String>> allScores =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(tempUnionKey, 0, -1);
        log.info("所有分类及其窗口总分：{}", allScores);
        if (allScores == null || allScores.isEmpty()) {
            log.info("窗口内无任何访问数据，跳过热点更新");
            cleanUp(tempUnionKey, lastCompleteSlice);
            return;
        }

        // 6. 获取最终热点阈值
        double finalThreshold = getFinalThreshold(allScores);

        // 7. 筛选出超过阈值的分类
        List<String> hotCategories = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : allScores) {
            Double score = tuple.getScore();
            if (score != null && score >= finalThreshold) {
                hotCategories.add(tuple.getValue());
            }
        }

        // 8. 更新热点缓存
        if (!hotCategories.isEmpty()) {
            String[] hotCategoryIds = hotCategories.stream().distinct().toArray(String[]::new);
            updateHotCategoryCache(hotCategoryIds);
            log.info("检测到{}个热点分类，阈值={}", hotCategories.size(), finalThreshold);
        } else {
            // 没有热点时，清空热点缓存
            clearHotCategoryCache();
            log.info("未检测到热点分类，已清空热点缓存");
        }

        // 9. 清理临时Key和过期时间片
        cleanUp(tempUnionKey, lastCompleteSlice);
    }

    /**
     * 获取最终热点阈值
     */
    private static double getFinalThreshold(Set<ZSetOperations.TypedTuple<String>> allScores) {
        // 动态计算热点阈值
        double totalScore = 0.0;
        for (ZSetOperations.TypedTuple<String> tuple : allScores) {
            Double score = tuple.getScore();
            if (score != null) {
                totalScore += score;
            }
        }
        // 计算所有分类访问量的平均值
        double avgScore = totalScore / allScores.size();
        // 当三十秒内一个分类的访问量超过所有分类访问量的平均值的3倍（DYNAMIC_THRESHOLD_MULTIPLE），则会认为该分类是热点
        double dynamicThreshold = avgScore * DYNAMIC_THRESHOLD_MULTIPLE;
        // 比较绝对热点阈值和动态阈值，取较小的
        // 使用绝对阈值可以避免所有分类访问量都很高，但同时动态阈值也很高导致无法被标记为热点的情况
        return Math.min(ABSOLUTE_HOT_THRESHOLD, dynamicThreshold);
    }

    /**
     * 清理临时Key和窗口外的时间片Key
     */
    private void cleanUp(String tempUnionKey, long currentCompleteSlice) {
        // 删除临时Union Key
        stringRedisTemplate.delete(tempUnionKey);

        // 主动删除窗口最老的那个时间片（注意：最老的时间片索引 = currentCompleteSlice - WINDOW_SIZE + 1 的前一个）
        long oldestSlice = currentCompleteSlice - WINDOW_SIZE; // 窗口外第一个
        stringRedisTemplate.delete(getTimeSliceKey(oldestSlice));
        // 额外：可再删除更老的（optional，因有TTL兜底）
    }

    /**
     * 生成时间片对应的Key
     */
    private String getTimeSliceKey(long timeSlice) {
        return CacheKeyConstants.CATEGORY_QPS_STATS_KEY + ":" + timeSlice;
    }

    /**
     * 原子更新热点分类缓存
     */
    private void updateHotCategoryCache(String[] hotCategoryIds) {
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            // 1. 将缓存Key转为字节数组（Redis底层使用字节）
            byte[] key = CacheKeyConstants.HOT_CATEGORY_IDS_KEY.getBytes(StandardCharsets.UTF_8);
            try {
                // 2. 开启事务
                connection.multi();
                // 3. 删除旧的集合（如果存在）
                connection.keyCommands().del(key);
                // 4. 如果新的热点列表不为空，则将其全部添加到Set中
                if (hotCategoryIds != null &&hotCategoryIds.length > 0) {
                    connection.setCommands().sAdd(key, Arrays.stream(hotCategoryIds)
                            .map(String::getBytes)
                            .toArray(byte[][]::new));
                }
                // 5. 提交事务
                connection.exec();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    /**
     * 清空热点分类缓存
     */
    private void clearHotCategoryCache() {
        stringRedisTemplate.delete(CacheKeyConstants.HOT_CATEGORY_IDS_KEY);
    }

    /**
     * 访问计数时，确保时间片Key有过期时间
     */
    public void incrementCategoryAccess(String categoryId) {
        // 获取当前时间片的索引
        long currentTimeSlice = System.currentTimeMillis() / 1000 / TIME_SLICE_SECONDS;
        // 获取时间片Key
        String sliceKey = getTimeSliceKey(currentTimeSlice);
        // 原子增加分数
        stringRedisTemplate.opsForZSet().incrementScore(sliceKey, categoryId, 1);
        // 每次操作后，重新设置过期时间，以保证活跃的时间片不会过期
        stringRedisTemplate.expire(sliceKey, TIME_SLICE_KEY_TTL_SECONDS, TimeUnit.SECONDS);
    }
}
