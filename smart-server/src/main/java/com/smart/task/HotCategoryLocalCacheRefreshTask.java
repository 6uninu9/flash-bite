package com.smart.task;

import com.smart.constant.CacheKeyConstants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Set;

/**
 * 热门分类检测器
 * 定时拉取热点分类缓存，保存到本地缓存中
 */
@Slf4j
@Component
public class HotCategoryLocalCacheRefreshTask {

    private final StringRedisTemplate stringRedisTemplate;

    // 本地缓存
    private final HashMap<String, Boolean> hotIds = new HashMap<>();

    public HotCategoryLocalCacheRefreshTask(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 初始化
    @PostConstruct
    public void init() {
        refresh();
    }

    // 每30秒执行一次
    @Scheduled(cron = "0/30 * * * * ?")
    public void refresh() {
        log.info("开始刷新本地热点缓存...");

        // 1. 获取所有热点分类id缓存
        Set<String> members = stringRedisTemplate.opsForSet().members(CacheKeyConstants.HOT_CATEGORY_IDS_KEY);

        // 2. 刷新本地缓存
        hotIds.clear();
        if (members != null && !members.isEmpty()) {
            for (String member : members) {
                hotIds.put(member, Boolean.TRUE); // Boolean.TRUE只是一个占位符，没有任何作用
            }
        }

        log.info("本地热点缓存刷新完毕...");
    }

    /**
     * 判断某个分类id是否是热点分类
     *
     * @param categoryId 分类id
     * @return 是否是热点分类
     */
    public boolean isHot(String categoryId) {
        return hotIds.containsKey(categoryId);
    }
}
