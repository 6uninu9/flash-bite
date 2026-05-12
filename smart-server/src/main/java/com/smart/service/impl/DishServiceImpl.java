package com.smart.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.smart.constant.CacheTimeConstant;
import com.smart.constant.CacheKeyConstants;
import com.smart.constant.StatusConstant;
import com.smart.entity.Dish;
import com.smart.entity.DishFlavor;
import com.smart.mapper.DishFlavorMapper;
import com.smart.mapper.DishMapper;
import com.smart.service.DishService;
import com.smart.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DishServiceImpl implements DishService {

    private final DishMapper dishMapper;

    private final DishFlavorMapper dishFlavorMapper;

    private final StringRedisTemplate stringRedisTemplate;

    public DishServiceImpl(DishMapper dishMapper, DishFlavorMapper dishFlavorMapper, RedisTemplate redisTemplate, StringRedisTemplate stringRedisTemplate) {
        this.dishMapper = dishMapper;
        this.dishFlavorMapper = dishFlavorMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 根据分类id查询菜品
     * @param categoryId 分类id
     * @return 菜品数据
     */
    @Override
    public List<DishVO> getDishListByCategoryId(Long categoryId) {

        // 1.构造redis中的key
        String key = CacheKeyConstants.DISH_CACHE_KEY_PREFIX + categoryId;

        // 2.查询redis中是否存在缓存数据
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        if (redisDataJson != null&& !redisDataJson.isEmpty()){
            // 2.1. 缓存存在，直接返回
            return JSONObject.parseArray(redisDataJson, DishVO.class);
        }

        // 3. 缓存不存在，构建菜品查询实体以查询数据库
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品

        // 4. 执行查询
        List<Dish> dishList = dishMapper.list(dish);

        // 5. 创建DishVO对象列表，用于封装查询结果
        List<DishVO> dishVOList = new ArrayList<>();

        // 6. 遍历查询结果，将Dish数据封装为DishVO对象，并添加到列表中
        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d, dishVO);

            // 4.1. 根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        // 7. 缓存不存在，将查询结果缓存到redis中
        // 7.1. 设置过期时间TTL（实现自动清理冷数据，节省缓存内存） 30分钟+随机值（避免缓存雪崩）
        long ttl = CacheTimeConstant.COMMON_TTL_SECONDS + ThreadLocalRandom.current().nextLong(CacheTimeConstant.RANDOM_TTL_SECONDS + 1);
        // 7.2. 缓存数据
        stringRedisTemplate.opsForValue().set(key, JSONObject.toJSONString(dishVOList), ttl, TimeUnit.SECONDS);

        // 5. 返回DishVO对象列表
        return dishVOList;
    }
}
