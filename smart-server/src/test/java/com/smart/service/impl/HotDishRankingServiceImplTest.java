package com.smart.service.impl;

import com.smart.entity.Dish;
import com.smart.enumeration.HotRankPeriod;
import com.smart.mapper.DishMapper;
import com.smart.properties.HotRankProperties;
import com.smart.vo.HotDishRankVO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分布式菜品热销榜核心行为单元测试
 */
class HotDishRankingServiceImplTest {

    private StringRedisTemplate stringRedisTemplate;
    private RocketMQTemplate rocketMQTemplate;
    private DishMapper dishMapper;
    private HashOperations<String, Object, Object> hashOperations;
    private ZSetOperations<String, String> zSetOperations;
    private HotRankMetrics metrics;
    private HotDishRankingServiceImpl service;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        rocketMQTemplate = mock(RocketMQTemplate.class);
        dishMapper = mock(DishMapper.class);
        hashOperations = mock(HashOperations.class);
        zSetOperations = mock(ZSetOperations.class);
        metrics = new HotRankMetrics(new SimpleMeterRegistry());

        HotRankProperties properties = new HotRankProperties();
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of());
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        service = new HotDishRankingServiceImpl(
                stringRedisTemplate, rocketMQTemplate, dishMapper, properties, metrics);
    }

    /**
     * 同一用户连续浏览同一菜品时复用固定去重键，并向Lua传入五分钟TTL
     */
    @Test
    void viewUsesRollingFiveMinuteDeduplication() {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);
        Dish dish = buildDish(1L, 10L, Dish.ENABLE);

        service.recordView(dish, 100L);
        service.recordView(dish, 100L);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(stringRedisTemplate, times(2)).execute(
                any(DefaultRedisScript.class), keysCaptor.capture(), argsCaptor.capture());
        assertEquals(keysCaptor.getAllValues().get(0).getFirst(), keysCaptor.getAllValues().get(1).getFirst());
        assertEquals("300", argsCaptor.getAllValues().get(0)[2]);
    }

    /**
     * Redis写入失败时只计失败指标并投递补偿消息，不向业务调用方抛异常
     */
    @Test
    void redisFailureDoesNotBreakBusinessFlow() {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("Redis不可用"));

        assertDoesNotThrow(() -> service.recordCart(buildDish(1L, 10L, Dish.ENABLE)));

        assertEquals(1L, metrics.snapshot().get("recordFailed"));
        verify(rocketMQTemplate).asyncSend(eq("hotDishRankCompensationTopic"), anyString(), any(SendCallback.class));
    }

    /**
     * 排名查询剔除停售菜品及不属于目标分类的菜品，并重新生成连续名次
     */
    @Test
    void categoryRankFiltersUnavailableAndMismatchedDishes() {
        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(new DefaultTypedTuple<>("2", 30D));
        tuples.add(new DefaultTypedTuple<>("3", 20D));
        tuples.add(new DefaultTypedTuple<>("1", 10D));
        when(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(9L))).thenReturn(tuples);
        when(dishMapper.selectBatchByIds(anyList())).thenReturn(List.of(
                buildDish(1L, 10L, Dish.ENABLE),
                buildDish(2L, 10L, Dish.DISABLE),
                buildDish(3L, 20L, Dish.ENABLE)));

        List<HotDishRankVO> result = service.topCategory(10L, HotRankPeriod.DAY, 2);

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().getRank());
        assertEquals(1L, result.getFirst().getDishId());
        assertEquals(10D, result.getFirst().getScore());
    }

    /**
     * 小时榜合并十二个五分钟片，周榜合并最近七个自然日
     */
    @Test
    void hourAndWeekRanksUseExpectedWindowKeys() {
        when(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(49L))).thenReturn(Set.of());

        service.topShop(HotRankPeriod.HOUR, 10);
        service.topShop(HotRankPeriod.WEEK, 10);

        ArgumentCaptor<Collection<String>> otherKeysCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(zSetOperations, times(2)).unionAndStore(
                anyString(), otherKeysCaptor.capture(), anyString());
        // unionAndStore的第一个Key单独传入，因此其余Key数量分别为11和6。
        assertEquals(11, otherKeysCaptor.getAllValues().get(0).size());
        assertEquals(6, otherKeysCaptor.getAllValues().get(1).size());
    }

    private Dish buildDish(Long id, Long categoryId, Integer status) {
        return Dish.builder()
                .id(id)
                .categoryId(categoryId)
                .name("测试菜品" + id)
                .price(new BigDecimal("10.00"))
                .status(status)
                .build();
    }
}
