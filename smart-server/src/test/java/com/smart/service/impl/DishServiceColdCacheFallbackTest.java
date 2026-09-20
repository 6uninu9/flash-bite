package com.smart.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.smart.entity.Dish;
import com.smart.entity.DishFlavor;
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
import org.redisson.client.RedisException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缓存降级链路单元测试
 * 验证：Redis 宕机/超时/连接异常时，冷/热缓存均能降级查询数据库，布隆过滤器异常时放行
 */
class DishServiceColdCacheFallbackTest {

    private DishMapper dishMapper;
    private DishFlavorMapper dishFlavorMapper;
    private StringRedisTemplate stringRedisTemplate;
    private RedissonClient redissonClient;
    private RBloomFilter<String> categoryBloomFilter;
    private BloomCacheService bloomCacheService;
    private RocketMQTemplate rocketMQTemplate;
    private Executor virtualTaskExecutor;
    private HotCategoryAutoDetectTask hotCategoryAutoDetectTask;
    private HotDishRankingService hotDishRankingService;
    private Cache<String, List<DishVO>> hotDishLocalCache;
    private ValueOperations<String, String> valueOperations;

    private DishServiceImpl dishService;

    @BeforeEach
    void setUp() {
        dishMapper = mock(DishMapper.class);
        dishFlavorMapper = mock(DishFlavorMapper.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        redissonClient = mock(RedissonClient.class);
        categoryBloomFilter = mock(RBloomFilter.class);
        bloomCacheService = mock(BloomCacheService.class);
        rocketMQTemplate = mock(RocketMQTemplate.class);
        virtualTaskExecutor = mock(Executor.class);
        hotCategoryAutoDetectTask = mock(HotCategoryAutoDetectTask.class);
        hotDishRankingService = mock(HotDishRankingService.class);
        hotDishLocalCache = mock(Cache.class);
        valueOperations = mock(ValueOperations.class);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        dishService = new DishServiceImpl(
                dishMapper, dishFlavorMapper, stringRedisTemplate, redissonClient,
                categoryBloomFilter, bloomCacheService, rocketMQTemplate, virtualTaskExecutor,
                hotCategoryAutoDetectTask, hotDishLocalCache, hotDishRankingService);
    }

    private Dish buildDish(Long id, String name, Long categoryId) {
        return Dish.builder().id(id).name(name).categoryId(categoryId)
                .price(new BigDecimal("10.00")).status(Dish.ENABLE).build();
    }

    /**
     * 冷缓存：Redis 宕机（连接异常，真实场景抛 RedisConnectionFailureException）时降级直查数据库
     */
    @Test
    void coldCategoryRedisDownDegradesToDb() {
        // 布隆过滤器放行，且判定为冷数据
        when(bloomCacheService.contains(any(), anyString())).thenReturn(false);
        when(hotCategoryAutoDetectTask.isHot("1")).thenReturn(false);
        // Redis 宕机：读取缓存抛连接失败异常（与 RedisSystemException 为兄弟异常，需分别捕获）
        when(valueOperations.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis down", new RuntimeException("Connection refused")));
        // 数据库返回一条菜品及口味
        when(dishMapper.list(any())).thenReturn(List.of(buildDish(1L, "红烧肉", 1L)));
        when(dishFlavorMapper.getByDishId(1L)).thenReturn(List.of(DishFlavor.builder().id(1L).dishId(1L).name("微辣").build()));

        List<DishVO> result = dishService.getDishListByCategoryId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("红烧肉", result.get(0).getName());
        assertEquals(1, result.get(0).getFlavors().size());
        // 降级链路确实查询了数据库
        verify(dishMapper, atLeastOnce()).list(any());
    }

    /**
     * 热缓存：Redis 超时时降级直查数据库并回写 L1 本地缓存
     */
    @Test
    void hotCategoryRedisTimeoutDegradesToDb() {
        when(bloomCacheService.contains(any(), anyString())).thenReturn(false);
        when(hotCategoryAutoDetectTask.isHot("1")).thenReturn(true);
        // Redis 超时：读取缓存抛超时异常（QueryTimeoutException 不属于 RedisSystemException，需单独覆盖）
        when(valueOperations.get(anyString()))
                .thenThrow(new QueryTimeoutException("Redis timeout", new RuntimeException("Read timed out")));
        when(dishMapper.list(any())).thenReturn(List.of(buildDish(2L, "清蒸鱼", 1L)));
        when(dishFlavorMapper.getByDishId(2L)).thenReturn(List.of(DishFlavor.builder().id(2L).dishId(2L).name("原味").build()));

        List<DishVO> result = dishService.getDishListByCategoryId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("清蒸鱼", result.get(0).getName());
        // 降级后回写 L1 本地缓存，供后续请求命中
        verify(hotDishLocalCache).put(eq("1"), anyList());
    }

    /**
     * 冷缓存：Redis 可达但回种写入失败时，仍返回数据库查询结果，不阻断读路径
     */
    @Test
    void coldBackfillSetFailsStillReturnsData() {
        when(bloomCacheService.contains(any(), anyString())).thenReturn(false);
        when(hotCategoryAutoDetectTask.isHot("1")).thenReturn(false);
        // 缓存未命中（get 返回 null），但回种 set 时 Redis 故障
        when(valueOperations.get(anyString())).thenReturn(null);
        when(dishMapper.list(any())).thenReturn(List.of(buildDish(3L, "宫保鸡丁", 1L)));
        when(dishFlavorMapper.getByDishId(3L)).thenReturn(List.of());
        doThrow(new RedisSystemException("Redis down", new RuntimeException("write fail")))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        List<DishVO> result = dishService.getDishListByCategoryId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("宫保鸡丁", result.get(0).getName());
    }

    /**
     * 布隆过滤器：Redis 宕机时放行查询（fail-open），避免防穿透保护反噬可用性
     */
    @Test
    void bloomFilterRedisDownFailsOpen() {
        BloomCacheService realService = new BloomCacheService();
        RBloomFilter<String> downFilter = mock(RBloomFilter.class);
        when(downFilter.contains(anyString())).thenThrow(new RedisException("Redis down"));

        // Redis 不可用时无法判断元素是否存在，直接放行（返回 false，即"可能存在"）
        assertFalse(realService.contains(downFilter, "1"));
    }
}
