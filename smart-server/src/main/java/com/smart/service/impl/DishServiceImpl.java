package com.smart.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.smart.constant.CacheTimeConstant;
import com.smart.constant.CacheKeyConstants;
import com.smart.constant.MessageConstant;
import com.smart.constant.StatusConstant;
import com.smart.data.RedisData;
import com.smart.dto.DishDTO;
import com.smart.dto.DishPageQueryDTO;
import com.smart.entity.Dish;
import com.smart.entity.DishFlavor;
import com.smart.exception.DeletionNotAllowedException;
import com.smart.mapper.DishFlavorMapper;
import com.smart.mapper.DishMapper;
import com.smart.result.PageResult;
import com.smart.service.BloomCacheService;
import com.smart.service.DishService;
import com.smart.task.HotCategoryAutoDetectTask;
import com.smart.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DishServiceImpl implements DishService {

    private final DishMapper dishMapper;

    private final DishFlavorMapper dishFlavorMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final RedissonClient redissonClient;

    // 注入对应业务的布隆过滤器
    @Qualifier("categoryBloomFilter")
    private final RBloomFilter<String> categoryBloomFilter;

    // 注入布隆过滤器缓存服务，对布隆过滤器进行操作
    private final BloomCacheService bloomCacheService;

    private final RocketMQTemplate rocketMQTemplate;

    @Qualifier("rebuildDishCacheExecutor")
    private final Executor rebuildDishCacheExecutor;

    private final HotCategoryAutoDetectTask hotCategoryAutoDetectTask;

    private static final String LOCK_CATEGORY_DISH_REBUILD = "lock:category:dish:rebuild";

    public DishServiceImpl(DishMapper dishMapper, DishFlavorMapper dishFlavorMapper, StringRedisTemplate stringRedisTemplate, RedissonClient redissonClient, RBloomFilter<String> categoryBloomFilter, BloomCacheService bloomCacheService, RocketMQTemplate rocketMQTemplate, Executor rebuildDishCacheExecutor, HotCategoryAutoDetectTask hotCategoryAutoDetectTask) {
        this.dishMapper = dishMapper;
        this.dishFlavorMapper = dishFlavorMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
        this.categoryBloomFilter = categoryBloomFilter;
        this.bloomCacheService = bloomCacheService;
        this.rocketMQTemplate = rocketMQTemplate;
        this.rebuildDishCacheExecutor = rebuildDishCacheExecutor;
        this.hotCategoryAutoDetectTask = hotCategoryAutoDetectTask;
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
        if (bloomCacheService.contains(categoryBloomFilter, categoryId.toString())) {
            // 1.1. 不存在，直接返回空集合
            /// 如果前端有错误需求，可以抛出业务异常，让全局异常处理器处理，比如返回错误信息"菜品不存在"
            log.info("布隆过滤器拦截-根据分类查询菜品-分类不存在");
            return List.of();
        }

        // 2. 记录categoryId的访问量，用于区分冷热数据 使用ZSet数据类型，便于记录访问量，即热度
        hotCategoryAutoDetectTask.incrementCategoryAccess(String.valueOf(categoryId));

        // 3. 判断当前categoryId是热数据还是冷数据 走不同分支
        if (hotCategoryAutoDetectTask.isHot(String.valueOf(categoryId))) {
            return getHotCategoryDishes(categoryId);
        } else {
            return getColdCategoryDishes(categoryId);
        }
    }

    /**
     * 保存菜品数据
     *
     * @param dishDTO 菜品数据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();

        //插入一条菜品
        BeanUtils.copyProperties(dishDTO, dish); //将dishDTO中的属性拷贝给dish
        dishMapper.insert(dish);

        Long dishId = dish.getId(); //获取当前菜品的id，即dish_id

        log.info("dishID:{}", dishId);

        //插入一条、零条或多条口味
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            flavors.forEach( //为每个口味设置dish_id
                    dishFlavor -> dishFlavor.setDishId(dishId)
            );
            dishFlavorMapper.insertBatch(flavors); //批量插入口味数据
        }

        // 将分类id加入布隆过滤器
        bloomCacheService.addToBloomFilter(categoryBloomFilter, dishDTO.getCategoryId().toString());

        // 删除冷缓存，哪怕是极端情况下产生了旧数据也有TTL兜底，而且冷缓存访问量不大，用户体验影响低
        deleteColdCache(dishDTO.getCategoryId());

        // 设置热缓存逻辑过期时间
        setHotCacheLogicalExpire(dishDTO.getCategoryId());
    }

    /**
     * 修改菜品数据
     *
     * @param dishDTO 菜品数据
     */
    @Override
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish); //规范化操作，因为dishDTO含有flavor数组，并不单纯是一个菜品实体类

        //修改菜品数据
        dishMapper.update(dish);

        //删除菜品相关的口味数据
        dishFlavorMapper.deleteByDishId(dishDTO.getId());

        //插入新的口味数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            flavors.forEach( //为每个口味设置dish_id
                    dishFlavor -> dishFlavor.setDishId(dishDTO.getId())
            );
            dishFlavorMapper.insertBatch(flavors); //批量插入口味数据
        }

        deleteColdCache(dishDTO.getCategoryId());

        setHotCacheLogicalExpire(dishDTO.getCategoryId());
    }

    /**
     * 批量删除菜品
     *
     * @param ids 菜品id列表
     */
    @Override
    public void deleteBatch(List<Long> ids) {
        //判断菜品是否处于起售状态
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            if (Objects.equals(dish.getStatus(), StatusConstant.ENABLE)) { //如果处于起售状态，则不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE); //抛出业务异常提示信息
            }
        }

        //根据id集合批量删除菜品数据
        dishMapper.deleteByIds(ids);

        //根据id集合批量删除与菜品关联的口味数据
        dishFlavorMapper.deleteByDishIds(ids);

        //循环删除菜品冷缓存
        ids.forEach(this::deleteColdCache);

        //循环设置热缓存逻辑过期时间
        ids.forEach(this::setHotCacheLogicalExpire);
    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO 菜品分页查询参数
     * @return 菜品分页结果
     */
    @Override
    public PageResult<DishVO> queryPage(DishPageQueryDTO dishPageQueryDTO) {
        try (Page<DishVO> p = PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize())) {
            p.doSelectPage(() -> dishMapper.queryPage(dishPageQueryDTO));
            return new PageResult<>(p.getTotal(), p.getResult());
        }
    }

    /**
     * 根据id查询菜品和对应的口味数据
     *
     * @param id 菜品id
     * @return 菜品数据
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        //查询菜品数据
        Dish dish = dishMapper.getById(id);

        //查询与菜品相关的口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);

        //将查询到的数据封装到VO中
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);

        return dishVO;
    }

    /**
     * 删除菜品冷缓存
     *
     * @param id id
     */
    private void deleteColdCache(Long id) {
        String coldCategoryKey = CacheKeyConstants.COLD_CATEGORY_KEY_PREFIX + id;
        try {
            stringRedisTemplate.delete(coldCategoryKey);
        } catch (Exception e) {
            log.error("删除冷缓存失败，key：{}", coldCategoryKey, e);
            rocketMQTemplate.asyncSend("dishCacheDeleteTopic", id, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("删除冷缓存成功，key：{}", coldCategoryKey);
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("删除冷缓存失败，key：{}", coldCategoryKey, throwable);
                }
            });
        }
    }

    /**
     * 设置热缓存逻辑过期
     * @param id id
     */
    private void setHotCacheLogicalExpire(Long id) {
        String hotCategoryKey = CacheKeyConstants.HOT_CATEGORY_KEY_PREFIX + id;

        // 判断有无对应的热缓存
        try {
            String redisDataJson = stringRedisTemplate.opsForValue().get(hotCategoryKey);
            if (redisDataJson != null) {
                // 缓存命中，将逻辑时间设置为过期，让下一次请求自动异步查询数据库刷新缓存
                RedisData redisData = JSONObject.parseObject(redisDataJson, RedisData.class);
                // 判断数据是否为空
                if (redisData.getData() == null) {
                    // 为空，标记为需要刷新，而不是误以为是缓存穿透
                    // 设置这个原因在于：
                    // 为了避免热缓存的缓存穿透，将redisData中的data置为null，表示缓存穿透，如果查询到data为null，则说明缓存穿透，直接返回空集合
                    // 所以在插入数据时，这里有可能查询到用于避免缓存穿透的空结果，所以为了不误以为是缓存穿透，进行异步刷新，就设置一个对象赋给data
                    redisData.setData(RedisData.UPDATING_MARKER);
                }
                redisData.setExpireTime(System.currentTimeMillis() - 1);
                stringRedisTemplate.opsForValue().set(hotCategoryKey, JSONObject.toJSONString(redisData));
            }
            // 缓存未命中，说明不是热点数据，不用设置
        } catch (Exception e) {
            log.error("设置热缓存逻辑过期时间失败，key：{}", hotCategoryKey, e);
            rocketMQTemplate.asyncSend("cacheLogicalExpireTopic", id, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("设置热缓存逻辑过期时间成功，key：{}", hotCategoryKey);
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("设置热缓存逻辑过期时间失败，key：{}", hotCategoryKey, throwable);
                }
            });
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
        // 为避免缓存穿透，我将查询数据库得到空结果的key对应的RedisData中的data字段设置为null
        // 只有Json字符串不为空且data字段不为空时，才缓存命中返回数据
        // 那为什么是置为null而不是空字符串呢？
        //   1. 语义一致性：null匹配 "数据库中不存在该数据" 的语义，
        //      而空字符串语义混乱"存在但值为空"或者"根本不存在"
        //   2. 类型安全性：null对所有 Java 类型（Long、Integer、自定义对象等）都安全，
        //      而空字符仅对 String 类型安全，其他类型反序列化直接抛出异常
        if (redisDataJson != null && JSONObject.parseObject(redisDataJson, RedisData.class).getData() != null) {

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
                // 2.3.1 未过期，修改最近一次访问时间
                redisData.setLastAccessTime(System.currentTimeMillis());
                stringRedisTemplate.opsForValue().set(key, JSONObject.toJSONString(redisData));
                // 2.3.2. 返回数据
                return dishVOList;
            }

            // 2.4. 过期，异步重建缓存
            asyncBuildHotCategoryCache(key, categoryId);

            // 2.5. 返回旧数据
            return dishVOList;
        } else if (redisDataJson != null) {
            // 3. 缓存存在但对应的data字段为null，则返回空结果
            log.info("热缓存空结果");
            return List.of();
        }

        // 4. 缓存不存在，异步建立缓存
        // 处理空结果缓存失效或热点key首次晋升缓存刚好失效
        asyncBuildHotCategoryCache(key, categoryId);

        // 5. 返回空数据
        return List.of();
    }


    private void asyncBuildHotCategoryCache(String key, Long categoryId) {
        // 逻辑时间过期，异步加锁重建缓存

        /*
          加非阻塞的锁 如果线程拿不到锁则直接返回旧数据，避免其他线程阻塞
          由于正常情况下重建缓存时间也就几十毫秒，
          所以设置3秒的持锁时间，即锁在3秒后释放，3秒内如果再次有查询请求则直接返回旧数据
        */

        // 异步重建缓存
        CompletableFuture.runAsync(() -> {
            // 1. 创建分布式锁
            RLock lock = redissonClient.getLock(LOCK_CATEGORY_DISH_REBUILD + categoryId);

            try {
                // 2. 尝试获取锁
                if (lock.tryLock(0, 3, TimeUnit.SECONDS)) { // 获取锁成功，进行缓存重建
                    log.info("锁获取成功，开始建立缓存");
                    RedisData redisData = new RedisData();

                    // 3. 查询数据库
                    Dish dish = new Dish();
                    dish.setCategoryId(categoryId);
                    dish.setStatus(StatusConstant.ENABLE);
                    List<Dish> dishList = dishMapper.list(dish);

                    // 4. 查询结果为空，则缓存空结果，避免缓存穿透
                    if (dishList == null || dishList.isEmpty()) {
                        redisData.setData(null);
                        // 空值的逻辑过期时间比物理过期时间少30秒
                        // 当逻辑过期时间到达时，物理过期时间也刚好到达，Redis 会自动删除这个 key 即使代码检查到逻辑过期，也不会触发异步更新
                        redisData.setExpireTime(System.currentTimeMillis() + (CacheTimeConstant.NULL_TTL_SECONDS - 30) * 1000);
                        redisData.setLastAccessTime(System.currentTimeMillis());
                        stringRedisTemplate.opsForValue().set(key,
                                JSONObject.toJSONString(redisData),
                                CacheTimeConstant.NULL_TTL_SECONDS,
                                TimeUnit.SECONDS);
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
                    redisData = RedisData.builder()
                            .data(dishVOList)
                            .expireTime(System.currentTimeMillis() + CacheTimeConstant.LOGICAL_EXPIRE_SECONDS * 1000)
                            .lastAccessTime(System.currentTimeMillis())
                            .build();
                    stringRedisTemplate.opsForValue().set(key, JSONObject.toJSONString(redisData)); // 将RedisData对象转为JSON字符串存进redis
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // 发送消息进行补偿重试
                sendRebuildCompensationMsg(categoryId);
                throw new RuntimeException("线程中断", e);
            } catch (Exception e) {
                log.error("重建菜品缓存异常：{}", e.getMessage());
                // 发送消息进行补偿重试
                sendRebuildCompensationMsg(categoryId);
                throw new RuntimeException(e);
            }
        }, rebuildDishCacheExecutor);
    }

    /**
     * 发送补偿消息
     *
     * @param categoryId 分类id
     */
    private void sendRebuildCompensationMsg(Long categoryId) {
        try {
            rocketMQTemplate.asyncSend("dishCacheRebuildTopic", categoryId, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("补偿消息发送成功，categoryId：{}", categoryId);
                }

                @Override
                public void onException(Throwable e) {
                    log.error("补偿消息发送失败，categoryId：{}", categoryId, e);
                }
            });
        } catch (Exception e) {
            log.error("发送补偿消息异常：{}，categoryId：{}", e.getMessage(), categoryId);
        }
    }
}
