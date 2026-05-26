package com.smart.task;

import com.alibaba.fastjson.JSONObject;
import com.smart.constant.CacheKeyConstants;
import com.smart.data.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 热点缓存僵尸数据清理任务
 */
@Component
@Slf4j
public class HotCacheCleanTask {

    private final StringRedisTemplate stringRedisTemplate;

    // 僵尸数据阈值：7天（毫秒）
    private static final long ZOMBIE_THRESHOLD = TimeUnit.DAYS.toMillis(7);

    public HotCacheCleanTask(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 定时任务：每天凌晨2点执行，清理查询时间超过阈值的热点缓存
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanZombieHotCache() {
        log.info("开始清理超过{}天未访问的热点僵尸缓存...", TimeUnit.MILLISECONDS.toDays(ZOMBIE_THRESHOLD));

        // 1. 配置 SCAN 命令
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(CacheKeyConstants.HOT_CATEGORY_KEY_PREFIX + "*")
                .count(1000)
                .build();

        // 2. 执行 SCAN 命令，处理每个 key
        try (Cursor<byte[]> cursor = stringRedisTemplate.execute(
                (RedisConnection connection) ->
                        connection.keyCommands().scan(scanOptions))) {
            if (cursor != null) {
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    try {
                        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
                        // 判断缓存数据是否为空
                        if (redisDataJson == null) {
                            continue;
                        }
                        // 解析缓存数据
                        RedisData redisData = JSONObject.parseObject(redisDataJson, RedisData.class);
                        // 获取最后访问时间
                        Long lastAccessTime = redisData.getLastAccessTime();
                        // 判断 lastAccessTime 字段是否存在
                        if (lastAccessTime == null) {
                            log.warn("热点缓存 {} 缺少 lastAccessTime 字段，跳过清理", key);
                            continue;
                        }
                        long now = System.currentTimeMillis();
                        if (now - lastAccessTime > ZOMBIE_THRESHOLD) {
                            // 使用异步删除 UNLINK 避免阻塞
                            stringRedisTemplate.unlink(key);
                            log.debug("清理僵尸热点缓存：{} (最后访问时间：{})", key, lastAccessTime);
                        }
                    } catch (Exception e) {
                        log.error("清理热点缓存失败，key：{}", key, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("SCAN扫描热点缓存过程中发生异常", e);
        }

        log.info("清理超过{}天未访问的热点僵尸缓存完毕...", TimeUnit.MILLISECONDS.toDays(ZOMBIE_THRESHOLD));
    }
}
