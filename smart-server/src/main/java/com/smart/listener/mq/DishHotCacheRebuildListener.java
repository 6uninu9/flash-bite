package com.smart.listener.mq;

import com.alibaba.fastjson.JSONObject;
import com.smart.constant.CacheKeyConstants;
import com.smart.constant.CacheTimeConstant;
import com.smart.constant.StatusConstant;
import com.smart.data.RedisData;
import com.smart.entity.Dish;
import com.smart.entity.DishFlavor;
import com.smart.mapper.DishFlavorMapper;
import com.smart.mapper.DishMapper;
import com.smart.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 菜品热缓存重建 MQ 消息监听器
 * 用于菜品热缓存重建失败的补偿重试
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = "dishHotCacheRebuildTopic", // 订阅的主题
        consumerGroup = "dish-cache-rebuild-consumer-group", // 消费者组
        consumeMode = ConsumeMode.CONCURRENTLY, // 消费模式 并发消费
        consumeThreadNumber = 32 // 并发消费线程数 处理器*2
)
public class DishHotCacheRebuildListener implements RocketMQListener<String> {

    private final StringRedisTemplate stringRedisTemplate;

    private final DishMapper dishMapper;

    private final DishFlavorMapper dishFlavorMapper;

    private static final String IDENTITY_KEY = "idempotent:dish:rebuild:";

    public DishHotCacheRebuildListener(StringRedisTemplate stringRedisTemplate, DishMapper dishMapper, DishFlavorMapper dishFlavorMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.dishMapper = dishMapper;
        this.dishFlavorMapper = dishFlavorMapper;
    }

    @Override
    public void onMessage(String message) {
        // 1. 消息校验与转换
        if (message == null || message.trim().isEmpty()){
            log.error("消息为空，无法处理。");
            return;
        }
        log.info("收到菜品热缓存重建消息，菜品ID：{}", message);
        long categoryId;
        try {
            categoryId = Long.parseLong(message);
        } catch (NumberFormatException e) {
            log.error("消息为空或者消息体格式错误，非数字类型或空字符串，无法处理。消息：{}", message, e);
            return;
        }

        // 2. 幂等性校验，避免消息重复消费
        if (Boolean.FALSE.equals(stringRedisTemplate.opsForValue().setIfAbsent(IDENTITY_KEY + categoryId, "", CacheTimeConstant.DUPLICATE_CHECK_TTL_SECONDS, TimeUnit.SECONDS))) {
            log.warn("菜品热缓存重建消息重复");
            return;
        }

        try {
            String key = CacheKeyConstants.HOT_CATEGORY_KEY_PREFIX + categoryId;

            // 3. 查询数据库
            Dish dish = new Dish();
            dish.setCategoryId(categoryId);
            dish.setStatus(StatusConstant.ENABLE);
            List<Dish> dishList = dishMapper.list(dish);

            // 4. 查询结果为空，则缓存空结果，避免缓存穿透
            if (dishList == null || dishList.isEmpty()) {
                stringRedisTemplate.opsForValue().set(key, "", CacheTimeConstant.NULL_TTL_SECONDS, TimeUnit.SECONDS);
                return;
            }

            // 5. 查询结果不为空，将数据缓存到redis中
            // 5.1. 封装查询结果
            List<DishVO> dishVOList = new ArrayList<>();
            for (Dish d : dishList) {
                DishVO dishVO = new DishVO();
                BeanUtils.copyProperties(d, dishVO);

                // 根据菜品id查询对应的口味
                List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

                dishVO.setFlavors(flavors);
                dishVOList.add(dishVO);
            }

            // 6. 缓存数据
            RedisData redisData1 = RedisData.builder()
                    .data(dishVOList)
                    .expireTime(System.currentTimeMillis() + CacheTimeConstant.LOGICAL_EXPIRE_SECONDS * 1000)
                    .lastAccessTime(System.currentTimeMillis())
                    .build();
            stringRedisTemplate.opsForValue().set(key, JSONObject.toJSONString(redisData1)); // 将RedisData对象转为JSON字符串存进redis
        } catch (Exception e) { // 建议是区分可恢复异常和不可恢复异常 如果是不可恢复异常应该直接结束而不是继续重试
            log.error("菜品热缓存重建异常：{}", e.getMessage());
            // 移除幂等标识 进行补偿重试
            stringRedisTemplate.delete(IDENTITY_KEY + categoryId);
            throw new RuntimeException(e);
        }
    }
}
