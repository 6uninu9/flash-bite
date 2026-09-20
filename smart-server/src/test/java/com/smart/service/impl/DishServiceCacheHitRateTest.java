package com.smart.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.smart.constant.CacheKeyConstants;
import com.smart.data.RedisData;
import com.smart.mapper.DishFlavorMapper;
import com.smart.mapper.DishMapper;
import com.smart.service.BloomCacheService;
import com.smart.service.HotDishRankingService;
import com.smart.task.HotCategoryAutoDetectTask;
import com.smart.vo.DishVO;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 菜品多级缓存命中率测试。
 *
 * <p>本测试使用固定热点分类模拟可重复的预热后访问场景，用于验证 L2 Redis
 * 首次命中后能够回填 L1 Caffeine，后续请求不会继续访问 Redis 或回源数据库。
 * 该结果只代表本测试定义的访问分布，不代表生产环境的实际命中率。</p>
 */
class DishServiceCacheHitRateTest {

    private static final Long CATEGORY_ID = 1L;
    private static final int REQUEST_COUNT = 100;

    private DishMapper dishMapper;
    private DishFlavorMapper dishFlavorMapper;
    private StringRedisTemplate stringRedisTemplate;
    private BloomCacheService bloomCacheService;
    private HotCategoryAutoDetectTask hotCategoryAutoDetectTask;
    private Cache<String, List<DishVO>> hotDishLocalCache;
    private ValueOperations<String, String> valueOperations;
    private DishServiceImpl dishService;

    @BeforeEach
    void setUp() {
        dishMapper = mock(DishMapper.class);
        dishFlavorMapper = mock(DishFlavorMapper.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBloomFilter<String> categoryBloomFilter = mock(RBloomFilter.class);
        bloomCacheService = mock(BloomCacheService.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        Executor virtualTaskExecutor = mock(Executor.class);
        hotCategoryAutoDetectTask = mock(HotCategoryAutoDetectTask.class);
        HotDishRankingService hotDishRankingService = mock(HotDishRankingService.class);
        valueOperations = mock(ValueOperations.class);

        hotDishLocalCache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .recordStats()
                .build();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(bloomCacheService.contains(categoryBloomFilter, CATEGORY_ID.toString())).thenReturn(false);
        when(hotCategoryAutoDetectTask.isHot(CATEGORY_ID.toString())).thenReturn(true);

        DishVO cachedDish = DishVO.builder()
                .id(1L)
                .name("缓存命中率测试菜品")
                .categoryId(CATEGORY_ID)
                .price(new BigDecimal("18.00"))
                .status(1)
                .build();
        RedisData redisData = RedisData.builder()
                .data(List.of(cachedDish))
                .expireTime(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5))
                .lastAccessTime(System.currentTimeMillis())
                .build();
        when(valueOperations.get(CacheKeyConstants.HOT_CATEGORY_KEY_PREFIX + CATEGORY_ID))
                .thenReturn(JSONObject.toJSONString(redisData));

        dishService = new DishServiceImpl(
                dishMapper,
                dishFlavorMapper,
                stringRedisTemplate,
                redissonClient,
                categoryBloomFilter,
                bloomCacheService,
                rocketMQTemplate,
                virtualTaskExecutor,
                hotCategoryAutoDetectTask,
                hotDishLocalCache,
                hotDishRankingService);
    }

    /**
     * 验证固定热点分类首次命中 L2 后，后续九十九次请求均命中 L1。
     */
    @Test
    void warmHotCategoryHasNinetyNinePercentL1HitRateWithoutDatabaseFallback() {
        for (int index = 0; index < REQUEST_COUNT; index++) {
            List<DishVO> result = dishService.getDishListByCategoryId(CATEGORY_ID);
            assertFalse(result.isEmpty());
            assertEquals("缓存命中率测试菜品", result.get(0).getName());
        }

        CacheStats cacheStats = hotDishLocalCache.stats();
        assertEquals(REQUEST_COUNT, cacheStats.requestCount());
        assertEquals(1L, cacheStats.missCount());
        assertEquals(REQUEST_COUNT - 1L, cacheStats.hitCount());
        assertEquals(0.99D, cacheStats.hitRate(), 0.000001D);

        verify(valueOperations, times(1))
                .get(CacheKeyConstants.HOT_CATEGORY_KEY_PREFIX + CATEGORY_ID);
        verifyNoInteractions(dishMapper, dishFlavorMapper);
    }
}
