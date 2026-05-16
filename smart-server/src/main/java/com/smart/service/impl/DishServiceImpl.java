package com.smart.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.smart.constant.CacheTimeConstant;
import com.smart.constant.CacheKeyConstants;
import com.smart.constant.StatusConstant;
import com.smart.data.RedisData;
import com.smart.entity.Dish;
import com.smart.entity.DishFlavor;
import com.smart.mapper.DishFlavorMapper;
import com.smart.mapper.DishMapper;
import com.smart.service.BloomCacheService;
import com.smart.service.DishService;
import com.smart.task.HotCategoryLocalCacheRefreshTask;
import com.smart.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private final HotCategoryLocalCacheRefreshTask hotCategoryLocalCacheRefreshTask;

    private final RedissonClient redissonClient;

    // 注入对应业务的布隆过滤器
    @Qualifier("dishBloomFilter")
    private final RBloomFilter<String> dishBloomFilter;

    // 注入布隆过滤器缓存服务，对布隆过滤器进行操作
    private final BloomCacheService bloomCacheService;

    private static final String LOCK_CATEGORY_DISH_REBUILD = "lock:category:dish:rebuild";

    public DishServiceImpl(DishMapper dishMapper, DishFlavorMapper dishFlavorMapper, StringRedisTemplate stringRedisTemplate, HotCategoryLocalCacheRefreshTask hotCategoryLocalCacheRefreshTask, RedissonClient redissonClient, RBloomFilter<String> dishBloomFilter, BloomCacheService bloomCacheService) {
        this.dishMapper = dishMapper;
        this.dishFlavorMapper = dishFlavorMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.hotCategoryLocalCacheRefreshTask = hotCategoryLocalCacheRefreshTask;
        this.redissonClient = redissonClient;
        this.dishBloomFilter = dishBloomFilter;
        this.bloomCacheService = bloomCacheService;
    }

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId 分类id
     * @return 菜品数据
     */
    @Override
    public List<DishVO> getDishListByCategoryId(Long categoryId) {
        // 1. 查询布隆过滤器是否存在该分类id，避免缓存穿透
        if (!bloomCacheService.contains(dishBloomFilter, categoryId.toString())) {
            // 1.1. 不存在，直接返回空集合
            /// 如果前端有错误需求，可以抛出业务异常，让全局异常处理器处理，比如返回错误信息"菜品不存在"
            log.info("布隆过滤器拦截-根据分类查询菜品-分类不存在");
            return List.of();
        }

        // 2. 记录categoryId的访问量，用于区分冷热数据 使用ZSet数据类型，便于记录访问量，即热度
        stringRedisTemplate.opsForZSet().incrementScore(CacheKeyConstants.CATEGORY_QPS_STATS_KEY, String.valueOf(categoryId), 1);

        // 3. 判断当前categoryId是热数据还是冷数据 走不同分支
        if (hotCategoryLocalCacheRefreshTask.isHot(String.valueOf(categoryId))) {
            return getHotCategoryDishes(categoryId);
        } else {
            return getColdCategoryDishes(categoryId);
        }
    }

    /**
     * 获取冷数据
     *
     * @param categoryId 分类id
     * @return 菜品数据
     */
    private List<DishVO> getColdCategoryDishes(Long categoryId) {
        // 1. 查询缓存
        String key = CacheKeyConstants.COLD_CATEGORY_KEY_PREFIX + categoryId;
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 缓存存在
        if (redisDataJson != null && !redisDataJson.isEmpty()) {
            log.info("冷缓存的命中");
            // 2.1. 缓存命中，直接返回
            return JSONObject.parseArray(redisDataJson, DishVO.class);
        } else if (redisDataJson != null) {
            // 2.2. 缓存空结果，可能是为了避免缓存穿透
            /// 如果前端有错误需求，可以抛出业务异常，让全局异常处理器处理
            log.info("冷缓存的空结果");
            return List.of();
        }

        // 3. 缓存不存在，但是热点数据，走热数据逻辑
        // 这一步主要针对缓存失效，由于标记热点的缓存和拉取热点缓存保存到本地不是同时进行的是有一个短暂的时间窗口，
        // 所以会发生key已经晋升为了热点数据，但是还没有缓存到本地，且此时原本的冷数据缓存刚好失效的极端场景，
        // 由此就会引发缓存击穿，所以在缓存未命中时得先手动拉取一次热点缓存判断是不是热点数据
        if (Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(CacheKeyConstants.HOT_CATEGORY_IDS_KEY, String.valueOf(categoryId)))) {
            log.info("冷缓存转为走热缓存分支");
            // 如果是热点数据，那么就走热数据逻辑，进行缓存重建
            return getHotCategoryDishes(categoryId);
        }

        // 4. 缓存不存在，且真不是热点数据，构建菜品查询实体以查询数据库回种冷缓存
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品

        // 5. 执行查询
        List<Dish> dishList = dishMapper.list(dish);

        // 6. 菜品数据不存在，缓存空结果并返回空集合
        if (dishList == null || dishList.isEmpty()) {
            stringRedisTemplate.opsForValue().set(key, "", CacheTimeConstant.NULL_TTL_SECONDS, TimeUnit.SECONDS);
            return List.of();
        }

        // 7. 创建DishVO对象列表，用于封装查询结果
        List<DishVO> dishVOList = new ArrayList<>();

        // 8. 遍历查询结果，将Dish数据封装为DishVO对象，并添加到列表中
        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d, dishVO);

            // 8.1. 根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        // 9. 将查询结果缓存到redis中
        // 9.1. 设置过期时间TTL（实现自动清理冷缓存，节省缓存内存） 30分钟+随机值（避免缓存雪崩）
        long ttl = CacheTimeConstant.COMMON_TTL_SECONDS + ThreadLocalRandom.current().nextLong(CacheTimeConstant.RANDOM_TTL_SECONDS + 1);
        // 9.2. 缓存数据
        stringRedisTemplate.opsForValue().set(key, JSONObject.toJSONString(dishVOList), ttl, TimeUnit.SECONDS);

        // 10. 返回DishVO对象列表
        return dishVOList;
    }

    /**
     * 获取热数据
     *
     * @param categoryId 分类id
     * @return 菜品数据
     */
    private List<DishVO> getHotCategoryDishes(Long categoryId) {
        // 1. 查询缓存
        String key = CacheKeyConstants.HOT_CATEGORY_KEY_PREFIX + categoryId;
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 缓存命中
        if (redisDataJson != null && !redisDataJson.isEmpty()) {

            log.info("热缓存命中");

            // 2.1. 缓存命中，将Json字符串反序列化为RedisData
            RedisData redisData = JSONObject.parseObject(redisDataJson, RedisData.class);

            // 2.2. 将redisData中的data提取为List<DishVO>
            /*
            JSONObject.toJSONString(Object)	序列化：Java 对象 → JSON 字符串
            JSONObject.parseArray(String, Class) 反序列化：JSON 数组 → List<T>
             */
            List<DishVO> dishVOList = JSONObject.parseArray(JSONObject.toJSONString(redisData.getData()), DishVO.class);


            // 2.3. 判断逻辑时间是否过期
            if (redisData.getExpireTime() > System.currentTimeMillis()) {
                // 2.3.1. 未过期，直接返回
                return dishVOList;
            }

            // 2.4. 过期，重建缓存
            return buildHotCategoryCache(key, categoryId);
        } else if (redisDataJson != null) {
            // 3. 缓存存在但值为空，返回空集合
            log.info("热缓存空结果");
            return List.of();
        }

        // 4. 缓存不存在，建立缓存 空结果缓存失效或热点key首次晋升缓存刚好失效
        return buildHotCategoryCache(key, categoryId);
    }

    /**
     * 加锁建立缓存
     *
     * @param categoryId 分类id
     * @return 菜品数据
     */
    private List<DishVO> buildHotCategoryCache(String key, Long categoryId) {
        // 逻辑时间过期，加锁重建缓存

        /*
          加非阻塞的锁 如果线程拿不到锁则直接返回旧数据，避免太多线程阻塞
          由于正常情况下重建缓存时间也就几十毫秒，
          所以设置3秒的持锁时间，即锁在3秒后释放，3秒内如果再次有查询请求则直接返回旧数据
        */

        List<DishVO> dishVOList = new ArrayList<>();

        // 1. 创建分布式锁
        RLock lock = redissonClient.getLock(LOCK_CATEGORY_DISH_REBUILD + categoryId);

        try {
            // 2. 尝试获取锁
            if (lock.tryLock(0, 3, TimeUnit.SECONDS)) { // 获取锁成功，进行缓存重建
                log.info("锁获取成功，开始建立缓存");
                // 3. 查询数据库
                Dish dish = new Dish();
                dish.setCategoryId(categoryId);
                dish.setStatus(StatusConstant.ENABLE);
                List<Dish> dishList = dishMapper.list(dish);

                // 4. 查询结果为空，则缓存空结果，避免缓存穿透
                if (dishList == null || dishList.isEmpty()) {
                    stringRedisTemplate.opsForValue().set(key, "", CacheTimeConstant.NULL_TTL_SECONDS, TimeUnit.SECONDS);
                    return List.of();
                }

                // 5. 查询结果不为空，将数据缓存到redis中
                // 5.1. 封装查询结果
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
                        .build();
                stringRedisTemplate.opsForValue().set(key, JSONObject.toJSONString(redisData1)); // 将RedisData对象转为JSON字符串存进redis

                // 7. 返回数据
                return dishVOList;
            }

            log.info("锁获取失败，正在返回旧数据：{}", dishVOList);

            // 8. 锁获取失败，直接返回旧数据
            return dishVOList;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程中断", e);
        } catch (Exception e) {
            log.error("发送消息异常：{}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
