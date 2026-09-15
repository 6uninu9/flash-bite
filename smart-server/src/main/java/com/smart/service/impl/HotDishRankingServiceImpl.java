package com.smart.service.impl;

import com.alibaba.fastjson2.JSON;
import com.smart.constant.CacheKeyConstants;
import com.smart.data.HotRankEvent;
import com.smart.dto.HotRankConfigDTO;
import com.smart.entity.Dish;
import com.smart.entity.OrderDetail;
import com.smart.enumeration.HotRankPeriod;
import com.smart.exception.SystemException;
import com.smart.mapper.DishMapper;
import com.smart.properties.HotRankProperties;
import com.smart.service.HotDishRankingService;
import com.smart.vo.HotDishRankVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.client.RedisTimeoutException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis ZSet的分布式菜品热销榜实现
 * <p>
 * 总体流程（建议对照 doc/project/闪食项目_代码全解.md ① 的时序图阅读）：
 * 1. 埋点：浏览(getUserDishDetail)/加购(ShoppingCartService.add)/下单(OrderService.submitOrder
 *    afterCommit)分别调用 recordView/recordCart/recordOrder，把"一次用户行为"抽象成一个 HotRankEvent。
 * 2. 记分：所有事件统一执行 hotRankRecord.lua —— 一段 Lua 内原子完成"事件幂等判断(SET NX) + 在
 *    全店榜/分类榜的时、日 4 个 ZSet 上对 member=菜品ID 做 ZINCRBY 累加"。事件只会被记一次，
 *    同一菜品的分数是多次行为累加的结果，而不是每个事件分开比较。
 * 3. 查询：queryRank 把窗口内多个分片/日期 ZSet 并集后按分数倒序取候选，再回表过滤
 *    下架/已删/错分类菜品，凑满 Top-N 返回。
 * 4. 故障隔离：写 Redis 失败时事件转 RocketMQ(hotDishRankCompensationTopic)，补偿消费端 replay()
 *    重放同一事件；事件幂等键保证"重放/重复投递不重复加分"，且任何异常都不影响主流程。
 * 5. 权重可运营调整：默认 1/3/10 来自 application.yml(smart.hot-rank)，运营端写入 Redis Hash 后，
 *    各实例通过 10 秒本地缓存刷新，实现不重启热更新。
 * 6. 与 HotCategoryAutoDetectTask（热点分类检测）的区别：后者属于缓存治理——用 ZSet 统计"分类访问量"
 *    并由 @Scheduled 定时任务做滑动窗口判定冷热；本榜是"用户行为产生的菜品业务热度"，由请求路径实时
 *    累加。两者目的不同（缓存路由 vs 业务榜单），因此一个用定时任务、一个用事件实时写入，不合并实现。
 */
@Slf4j
@Service
public class HotDishRankingServiceImpl implements HotDishRankingService {

    // 补偿Topic
    private static final String COMPENSATION_TOPIC = "hotDishRankCompensationTopic";
    // 天榜Key的过期时间，9天。周榜查询需要并集最近7个天榜Key，9天>7天+安全余量，
    // 保证最旧的一天天榜在彻底滑出周榜窗口之前不会提前过期
    private static final long DAY_KEY_TTL_SECONDS = TimeUnit.DAYS.toSeconds(9);
    // 事件去重Key的过期时间，9天。加购/下单事件可能在较长的时间窗口内被 MQ 补偿/重试，
    // 幂等键必须比"最后一次可能的补偿投递"活得更久，否则同一事件可能被重复累加
    private static final long EVENT_DEDUP_TTL_SECONDS = TimeUnit.DAYS.toSeconds(9);
    // 临时Key的过期时间，30秒。小时榜/周榜查询会先生成临时聚合Key，finally 中会主动删除；
    // 该 TTL 只是兜底，防止删除失败时临时Key长期残留在 Redis 中
    private static final long TEMP_KEY_TTL_SECONDS = 30;
    // 候选倍数：取 TopN 时先从 ZSet 拉 limit*CANDIDATE_MULTIPLE 个候选，因为返回前要过滤
    // 已下架/已删除/不属于目标分类的菜品，若只取 limit 个可能在过滤后凑不满 Top-N；
    // 取 5 倍兼顾"过滤后有足够余量"与"不一次性拉取整张 ZSet"两个目标
    private static final int CANDIDATE_MULTIPLE = 5;
    // 日期格式化器，用于把事件时间格式化为 yyyyMMdd 字符串（构成天榜Key的后缀）
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    // 记录事件的Lua脚本
    private static final DefaultRedisScript<Long> RECORD_EVENT_SCRIPT;

    static {
        RECORD_EVENT_SCRIPT = new DefaultRedisScript<>();
        RECORD_EVENT_SCRIPT.setLocation(new ClassPathResource("hotRankRecord.lua"));
        RECORD_EVENT_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final DishMapper dishMapper;
    private final HotRankProperties properties;
    private final HotRankMetrics metrics;

    // 运营权重在本地实例的"缓存副本"：每次记分前先查它，避免每个事件都去读 Redis。
    // volatile 保证多线程下可见性：运营端/刷新线程修改后，其它线程能立即看到新值。
    private volatile HotRankConfigDTO currentConfig;
    // 上次成功刷新配置的时间戳（毫秒）：用于 10 秒节流，即 configCacheSeconds 秒内最多去 Redis 刷新一次。
    // volatile 配合下方 refreshConfigIfNecessary 的双重检查使用。
    private volatile long lastConfigRefreshMillis;

    public HotDishRankingServiceImpl(StringRedisTemplate stringRedisTemplate,
                                     RocketMQTemplate rocketMQTemplate,
                                     DishMapper dishMapper,
                                     HotRankProperties properties,
                                     HotRankMetrics metrics) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rocketMQTemplate = rocketMQTemplate;
        this.dishMapper = dishMapper;
        this.properties = properties;
        this.metrics = metrics;
        this.currentConfig = defaultConfig();
    }

    /**
     * 记录菜品浏览事件
     * @param dish   菜品
     * @param userId 用户ID
     */
    @Override
    public void recordView(Dish dish, Long userId) {
        long now = System.currentTimeMillis();
        // 每次浏览都生成新的事件ID：避免固定分桶边界重复计分
        String eventId = "view:" + userId + ":" + dish.getId();
        // 记录浏览事件
        // 浏览键在五分钟后自然过期，实现连续时间窗口去重，避免固定分桶边界重复计分
        recordSafely(buildEvent(eventId, dish, getConfig().getViewWeight(), now,
                properties.getViewDedupSeconds()));
    }

    /**
     * 记录购物车添加事件
     * @param dish 菜品
     */
    @Override
    public void recordCart(Dish dish) {
        long now = System.currentTimeMillis();
        // 每次加购都生成新的 UUID 事件键：同一用户反复加购同一道菜会被视作多次有效热度（都累加）。
        // 幂等键只防"同一次加购被重复执行/补偿重放"，不与"用户维度去重"混淆（浏览才有用户+菜品维度的去重）。
        String eventId = "cart:" + UUID.randomUUID();
        recordSafely(buildEvent(eventId, dish, getConfig().getCartWeight(), now,
                EVENT_DEDUP_TTL_SECONDS));
    }

    /**
     * 记录下单事件
     * @param orderId     订单ID
     * @param orderDetails 订单详情
     */
    @Override
    public void recordOrder(Long orderId, List<OrderDetail> orderDetails) {
        if (orderDetails == null || orderDetails.isEmpty()) {
            return;
        }

        // 同一菜品的不同口味/多份明细只产生一个下单事件，避免一张订单对同一道菜重复加权。
        // 因此先按菜品ID去重：用 LinkedHashSet 而不是 HashSet，去重的同时按"明细中首次出现的顺序"
        // 保留插入顺序，使一个订单产生的多个下单事件顺序稳定可预期（与明细顺序一致，便于排查与日志对照）。
        Set<Long> dishIds = new LinkedHashSet<>();
        // 提取订单详情中的菜品ID
        orderDetails.stream().map(OrderDetail::getDishId).filter(Objects::nonNull).forEach(dishIds::add);
        if (dishIds.isEmpty()) {
            return;
        }

        Map<Long, Dish> dishMap = new HashMap<>();
        // 批量查询菜品信息
        dishMapper.selectBatchByIds(new ArrayList<>(dishIds)).forEach(dish -> dishMap.put(dish.getId(), dish));
        // 获取下单权重
        double weight = getConfig().getOrderWeight();
        long now = System.currentTimeMillis();
        // 记录每个菜品的下单事件
        for (Long dishId : dishIds) {
            Dish dish = dishMap.get(dishId);
            if (dish != null) {
                recordSafely(buildEvent("order:" + orderId + ":" + dishId, dish, weight, now,
                        EVENT_DEDUP_TTL_SECONDS));
            }
        }
    }

    /**
     * 查询全店热销榜
     */
    @Override
    public List<HotDishRankVO> topShop(HotRankPeriod period, int limit) {
        // 查询全店热销榜
        return queryRank(null, period, normalizeLimit(limit));
    }

    /**
     * 查询分类热销榜
     * @param categoryId 分类ID
     * @param period     热销榜周期
     * @param limit      热销榜限制
     */
    @Override
    public List<HotDishRankVO> topCategory(Long categoryId, HotRankPeriod period, int limit) {
        return queryRank(categoryId, period, normalizeLimit(limit));
    }

    /**
     * 获取当前生效的运营权重
     */
    @Override
    public HotRankConfigDTO getConfig() {
        // 1. 尝试从缓存中刷新权重配置
        refreshConfigIfNecessary();
        // 2. 获取当前的权重配置
        HotRankConfigDTO config = currentConfig;
        // 3. 封装权重配置为DTO并返回
        return new HotRankConfigDTO(config.getViewWeight(), config.getCartWeight(), config.getOrderWeight());
    }

    /**
     * 更新运营配置（由商家端 PUT /admin/hot-rank/config 调用）。
     * <p>
     * 为什么把权重写进 Redis Hash(HOT_DISH_WEIGHT_CONFIG_KEY) 而不是本地/配置文件：
     * <br>
     * 1) 榜单是跨实例共享的数据，只有写到 Redis，一次修改才能让所有实例用同一份权重计分，无需逐台改配置重启；
     * <br>
     * 2) Hash 的每个 field 对应一个权重，可只改其中一个而不覆盖其它字段，也便于 hgetall 一次读回三个值；
     * <br>
     * 3) 当前实例写完立刻刷新本地副本（不等 10 秒），其它实例最迟在下一个 10 秒刷新周期内生效。
     */
    @Override
    public void updateConfig(HotRankConfigDTO configDTO) {
        // 1. 构建权重配置的Hash键值对（field: viewWeight / cartWeight / orderWeight）
        Map<String, String> values = new HashMap<>();
        values.put("viewWeight", String.valueOf(configDTO.getViewWeight()));
        values.put("cartWeight", String.valueOf(configDTO.getCartWeight()));
        values.put("orderWeight", String.valueOf(configDTO.getOrderWeight()));
        try {
            // 2. 将权重配置写入Redis（hputAll 一次写 3 个 field）
            stringRedisTemplate.opsForHash().putAll(CacheKeyConstants.HOT_DISH_WEIGHT_CONFIG_KEY, values);
            // 3. 更新当前实例的本地副本，立即生效，无需等待 10 秒刷新
            currentConfig = new HotRankConfigDTO(configDTO.getViewWeight(), configDTO.getCartWeight(), configDTO.getOrderWeight());
            // 4. 更新配置刷新时间，避免接下来 10 秒内又从 Redis 覆盖掉刚写入的值
            lastConfigRefreshMillis = System.currentTimeMillis();
        } catch (RedisConnectionFailureException | RedisSystemException | QueryTimeoutException | RedisTimeoutException e) {
            throw new SystemException("热销榜权重配置更新失败", e);
        }
    }

    /**
     * 获取指标数据
     */
    @Override
    public Map<String, Long> getMetrics() {
        return metrics.snapshot();
    }

    /**
     * 热销榜事件重放（由 HotDishRankCompensationListener 在 Redis 故障补偿场景下调用）。
     * <p>
     * 与 recordSafely 的区别：这里不吞异常，失败会直接抛给 RocketMQ 触发消息重试；
     * 重放仍执行同一个 Lua(事件幂等键 SET NX)，因此重复投递/多次补偿不会重复累加分数。
     */
    @Override
    public void replay(HotRankEvent event) {
        try {
            Long result = executeEvent(event);
            if (Objects.equals(result, 1L)) {
                metrics.recorded();
            } else {
                metrics.duplicated();
            }
        } catch (RuntimeException e) {
            // 重放失败必须抛给RocketMQ触发重试，同时纳入失败指标。
            metrics.recordFailed();
            throw e;
        }
    }

    /**
     * 记录事件
     * Redis异常时发送补偿消息且不影响业务主流程
     * <p>
     * 执行结果语义：Lua 返回 1 = 该事件首次计分成功(recorded)；返回 0 = 事件幂等键已存在，
     * 即同一事件被重复触发/重放(duplicated)。Redis 连接/超时类异常 → 转 RocketMQ 补偿；
     * 其它未预期异常仅记录日志——两种异常都不会向上抛，调用方(详情/加购/下单)永远感知不到热销榜故障。
     */
    private void recordSafely(HotRankEvent event) {
        try {
            // 尝试记录事件，如果成功则更新指标数据
            Long result = executeEvent(event);
            if (Objects.equals(result, 1L)) {
                metrics.recorded();
            } else {
                metrics.duplicated();
            }
        } catch (RedisConnectionFailureException | RedisSystemException | QueryTimeoutException | RedisTimeoutException e) {
            metrics.recordFailed();
            log.warn("热销榜事件写入Redis失败，转入补偿队列，eventId={}", event.getEventId(), e);
            sendCompensation(event);
        } catch (Exception e) {
            metrics.recordFailed();
            log.error("热销榜事件记录发生未预期异常，eventId={}", event.getEventId(), e);
        }
    }

    /**
     * 使用Lua保证事件去重及四个榜单Key的更新原子完成
     */
    private Long executeEvent(HotRankEvent event) {
        // 构建事件Key列表，顺序必须与 Lua 脚本里的 KEYS 下标一一对应：
        // KEYS[1]=事件幂等键；KEYS[2]=全店-小时切片；KEYS[3]=分类-小时切片；
        // KEYS[4]=全店-日键；KEYS[5]=分类-日键。
        List<String> keys = buildEventKeys(event);
        // 小时切片Key的TTL：单个时间片秒数 × (窗口切片数 + 2)。
        // 原因：一个切片Key最晚"在它被写入的那个时间片末尾被写"，而它要被小时榜读取到
        // "当前分片滑到它之后第 windowSlices-1 片"为止，即最多需要存活一个完整窗口(12片)的长度；
        // 再 +2 片作为边界冗余（写入时刻恰在切片边界、查询有延迟、多实例时钟偏差），
        // 保证窗口还没完全滑出时切片Key绝不提前过期（如只 +1，边界场景下可能少几片数据）。
        long hourKeyTtl = (long) properties.getSliceSeconds() * (properties.getWindowSlices() + 2);
        // 执行Lua脚本。注意语义：ZSet 的 member 是"菜品ID"（ARGV[1]），score 是"该菜品的累计热度"，
        // 事件幂等键(KEYS[1]) 与 member 是两回事——不同事件的 key 各不相同，但它们最终都累加在
        // 同一个菜品ID成员上，菜与菜之间比较的就是这个 score（见 buildRankResult）。
        return stringRedisTemplate.execute(
                RECORD_EVENT_SCRIPT,
                keys, // 幂等键：KEYS[1]，ZSet的key：KEYS[2]/KEYS[3]/KEYS[4]/KEYS[5]
                String.valueOf(event.getDishId()), // ARGV[1] 菜品ID → ZSet的member
                String.valueOf(event.getScore()), // ARGV[2] 事件分数：本次对该菜品累加的权重，member的对应的score
                String.valueOf(event.getDedupSeconds()), // ARGV[3] 幂等键TTL：浏览=300s滚动去重；加购/下单=9天
                String.valueOf(hourKeyTtl), // ARGV[4] 小时切片Key(KEYS[2]/[3])的TTL
                String.valueOf(DAY_KEY_TTL_SECONDS) // ARGV[5] 日键(KEYS[4]/[5])的TTL
        );
    }

    /**
     * 构建事件Key列表，包含去重Key、小时Key和天Key
     */
    private List<String> buildEventKeys(HotRankEvent event) {
        // 计算小时切片：事件发生时间除以切片秒数=小时切片
        long slice = event.getEventTime() / TimeUnit.SECONDS.toMillis(properties.getSliceSeconds());
        // 计算天：事件发生时间格式化为天
        String day = toDay(event.getEventTime()).format(DAY_FORMATTER);
        return List.of(
                // 事件幂等键
                CacheKeyConstants.HOT_DISH_EVENT_DEDUP_KEY_PREFIX + event.getEventId(),
                // 全店小时切片key
                CacheKeyConstants.HOT_DISH_SHOP_RANK_KEY_PREFIX + "hour:" + slice,
                // 分类小时切片key
                CacheKeyConstants.HOT_DISH_CATEGORY_RANK_KEY_PREFIX + event.getCategoryId() + ":hour:" + slice,
                // 全店天切片key
                CacheKeyConstants.HOT_DISH_SHOP_RANK_KEY_PREFIX + "day:" + day,
                // 分类天切片key
                CacheKeyConstants.HOT_DISH_CATEGORY_RANK_KEY_PREFIX + event.getCategoryId() + ":day:" + day
        );
    }

    /**
     * 构建热销榜事件
     * @param eventId      事件ID
     * @param dish         菜品
     * @param score        事件分数
     * @param eventTime    事件时间
     * @param dedupSeconds 去重时间
     * @return 热销榜事件
     */
    private HotRankEvent buildEvent(String eventId, Dish dish, double score, long eventTime, long dedupSeconds) {
        return HotRankEvent.builder()
                .eventId(eventId)
                .dishId(dish.getId())
                .categoryId(dish.getCategoryId())
                .score(score)
                .eventTime(eventTime)
                .dedupSeconds(dedupSeconds)
                .build();
    }

    /**
     * 查询热销榜
     */
    private List<HotDishRankVO> queryRank(Long categoryId, HotRankPeriod period, int limit) {
        String temporaryKey = null;
        try {
            // 1. 构建查询Key列表
            List<String> sourceKeys = buildQueryKeys(categoryId, period);
            String queryKey;
            if (sourceKeys.size() == 1) {
                // 如果查询Key列表只有一个元素，则直接使用该Key
                queryKey = sourceKeys.getFirst();
            } else {
                // 如果查询Key列表有多个元素，则使用临时Key进行并集操作
                // 构造临时key：hot:rank:temp:xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx（uuid）
                temporaryKey = CacheKeyConstants.HOT_DISH_TEMP_RANK_KEY_PREFIX + UUID.randomUUID();
                // 将多个Key的ZSet数据进行并集操作，并存储到临时Key中
                stringRedisTemplate.opsForZSet().unionAndStore(
                        sourceKeys.getFirst(), sourceKeys.subList(1, sourceKeys.size()), temporaryKey);
                // 设置临时Key的过期时间，节省内存
                stringRedisTemplate.expire(temporaryKey, TEMP_KEY_TTL_SECONDS, TimeUnit.SECONDS);
                queryKey = temporaryKey;
            }

            // 放大查询菜品（或某个分类下菜品）的数量，确保结果足够
            long candidateLimit = Math.max(limit, (long) limit * CANDIDATE_MULTIPLE);
            // 查询热销榜前candidateLimit - 1个菜品（或某个分类下菜品）及其分数
            Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                    .reverseRangeWithScores(queryKey, 0, candidateLimit - 1);
            // 构建热销榜结果
            List<HotDishRankVO> result = buildRankResult(tuples, categoryId, limit);
            // 记录查询成功的事件
            metrics.querySucceeded();
            return result;
        } catch (RedisConnectionFailureException | RedisSystemException | QueryTimeoutException | RedisTimeoutException e) {
            // 记录查询失败的事件
            metrics.queryFailed();
            log.warn("查询热销榜失败，categoryId={}，period={}", categoryId, period, e);
            return List.of();
        } finally {
            if (temporaryKey != null) {
                try {
                    stringRedisTemplate.delete(temporaryKey);
                } catch (Exception e) {
                    log.warn("删除热销榜临时Key失败，key={}", temporaryKey, e);
                }
            }
        }
    }

    /**
     * 构建查询热销榜的Key列表
     * @param categoryId 类别ID
     * @param period     时间周期
     * @return 查询Key列表
     */
    private List<String> buildQueryKeys(Long categoryId, HotRankPeriod period) {
        // 如果查询时间周期为小时，则构建小时榜Key列表
        if (period == HotRankPeriod.HOUR) {
            long currentSlice = System.currentTimeMillis() / TimeUnit.SECONDS.toMillis(properties.getSliceSeconds());
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < properties.getWindowSlices(); i++) {
                keys.add(hourKey(categoryId, currentSlice - i));
            }
            return keys;
        }

        // 如果查询时间周期为天，则构建天榜Key列表
        LocalDate today = LocalDate.now();
        if (period == HotRankPeriod.DAY) {
            return List.of(dayKey(categoryId, today));
        }

        // 如果查询时间周期为周，则构建周榜Key列表
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 7; i++) { // 周榜就是天榜的特殊形式，所以这里直接使用7天天榜的Key
            keys.add(dayKey(categoryId, today.minusDays(i)));
        }
        return keys;
    }

    /**
     * 构建小时榜Key
     * @param categoryId 类别ID
     * @param slice      时间切片
     * @return 小时榜Key
     */
    private String hourKey(Long categoryId, long slice) {
        // 如果categoryId为null，则返回全店菜品榜Key，否则返回指定分类中菜品榜Key
        return categoryId == null
                ? CacheKeyConstants.HOT_DISH_SHOP_RANK_KEY_PREFIX + "hour:" + slice
                : CacheKeyConstants.HOT_DISH_CATEGORY_RANK_KEY_PREFIX + categoryId + ":hour:" + slice;
    }

    /**
     * 构建天榜Key
     * @param categoryId 类别ID
     * @param day        时间
     * @return 天榜Key
     */
    private String dayKey(Long categoryId, LocalDate day) {
        String date = day.format(DAY_FORMATTER);
        return categoryId == null
                ? CacheKeyConstants.HOT_DISH_SHOP_RANK_KEY_PREFIX + "day:" + date
                : CacheKeyConstants.HOT_DISH_CATEGORY_RANK_KEY_PREFIX + categoryId + ":day:" + date;
    }

    /**
     * 构建热销榜结果
     * @param tuples             热销候选结果
     * @param expectedCategoryId 预期的类别ID
     * @param limit              热销榜结果数量限制
     * @return 热销榜结果
     *
     * 注意：tuples 由 reverseRangeWithScores 返回，已按 score（菜品累计热度）从大到小排好序；
     * ZSet 的 member 是菜品ID，score 是"该菜品被所有事件累加后的总热度"——不同事件累加在同一个
     * member 上，菜与菜之间比较的就是这个 score，事件去重键(KEYS[1])不参与排序比较。
     * 本方法只负责"过滤 + 组装 VO"，过滤后按遍历顺序 append 即为正确排名。
     */
    private List<HotDishRankVO> buildRankResult(Set<ZSetOperations.TypedTuple<String>> tuples,
                                                 Long expectedCategoryId,
                                                 int limit) {
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        // 提取菜品ID列表
        List<Long> dishIds = tuples.stream().map(ZSetOperations.TypedTuple::getValue)
                .filter(Objects::nonNull).map(Long::valueOf).toList();
        Map<Long, Dish> dishMap = new HashMap<>();
        // 根据菜品ID列表查询菜品信息
        dishMapper.selectBatchByIds(dishIds).forEach(dish -> dishMap.put(dish.getId(), dish));

        List<HotDishRankVO> result = new ArrayList<>();

        // 遍历热销候选结果
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Dish dish = dishMap.get(Long.valueOf(Objects.requireNonNull(tuple.getValue())));
            if (dish == null || !Objects.equals(dish.getStatus(), Dish.ENABLE)
                    || expectedCategoryId != null && !Objects.equals(dish.getCategoryId(), expectedCategoryId)) {
                continue;
            }
            result.add(HotDishRankVO.builder()
                    .rank(result.size() + 1)
                    .score(tuple.getScore())
                    .dishId(dish.getId())
                    .name(dish.getName())
                    .categoryId(dish.getCategoryId())
                    .price(dish.getPrice())
                    .image(dish.getImage())
                    .description(dish.getDescription())
                    .build());
            if (result.size() == limit) {
                break;
            }
        }
        return result;
    }

    /**
     * 将传入的 limit 参数归一化为 [1, maxLimit] 范围内的整数，
     * 避免出现非法的 limit 值，比如0，负数等。
     */
    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, properties.getMaxLimit()));
    }

    /**
     * 将事件发生时间转换为 LocalDate
     * @param eventTime 事件发生时间
     * @return LocalDate
     */
    private LocalDate toDay(long eventTime) {
        return Instant.ofEpochMilli(eventTime).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * 如果配置缓存已过期，则从Redis中读取配置。
     * <p>
     * 权重配置整体放在 Redis Hash（HOT_DISH_WEIGHT_CONFIG_KEY）中跨实例共享、支持运营热更新；
     * 每个实例再维护一个 configCacheSeconds(=10) 秒的本地副本 currentConfig，避免每个事件都打 Redis。
     * 本方法 = 本地副本的"按需刷新"：这里的"过期"仅指本地副本超过 10 秒有效期，需要在下次记分/查榜前
     * 到 Redis 拉取最新值，而不是说配置本身会被 Redis 删除。
     */
    private void refreshConfigIfNecessary() {
        long now = System.currentTimeMillis();
        // 第一次检查（无锁）：绝大多数请求落在 10 秒有效期内，直接命中本地副本返回
        // 不进锁、不打 Redis，保证高频记分路径几乎没有额外开销。
        if (now - lastConfigRefreshMillis < TimeUnit.SECONDS.toMillis(properties.getConfigCacheSeconds())) {
            return;
        }
        // 只有副本"刚过期"的少数请求才走到这里。synchronized(this) 是实例级互斥锁，
        // this = 本 HotDishRankingServiceImpl 单例 Bean，保证同一时刻只有一个线程去读 Redis 并更新副本。
        // 为什么 synchronized 就够：这是进程内的轻量节流刷新（每个实例各自刷新自己的副本），
        // 用不到 ReentrantLock 的公平/可中断能力，也不需要 Redisson 分布式锁（不存在跨实例共享写入）。
        // 影响范围：只会阻塞"同样要进入这个同步块"的线程；recordView/recordCart/recordOrder 等
        // 业务方法并不在此同步块内，不会被阻塞；且临界区只有一次 hgetall + 两次赋值，耗时极短。
        synchronized (this) { // this是指当前 HotDishRankingServiceImpl 对象
            // 第二次检查（double-check）：第一个线程刷新完成会把 lastConfigRefreshMillis 更新为 now，
            // 后面排队进入的线程发现副本又"未过期"便直接返回，避免并发到达时多个线程重复查 Redis。
            if (now - lastConfigRefreshMillis < TimeUnit.SECONDS.toMillis(properties.getConfigCacheSeconds())) {
                return;
            }
            try {
                // 从Redis中读取权重配置
                Map<Object, Object> values = stringRedisTemplate.opsForHash()
                        .entries(CacheKeyConstants.HOT_DISH_WEIGHT_CONFIG_KEY);
                if (!values.isEmpty()) {
                    currentConfig = new HotRankConfigDTO(
                            Double.valueOf(values.get("viewWeight").toString()),
                            Double.valueOf(values.get("cartWeight").toString()),
                            Double.valueOf(values.get("orderWeight").toString()));
                }
            } catch (Exception e) {
                // 配置读取失败时继续使用最近一次有效配置，不能影响业务事件。
                log.warn("读取热销榜运营权重失败，继续使用当前配置", e);
            } finally {
                // 无论刷新成功还是失败，都把刷新时间置为 now：失败时同样进入 10 秒冷却。
                // 这样 Redis 故障期间不会"每个请求都尝试连一次 Redis"（每次连接都超时会把故障放大），
                // 而是继续沿用最近一次成功读到的有效配置，见上方 catch 的兜底逻辑。
                lastConfigRefreshMillis = now;
            }
        }
    }

    /**
     * 获取默认配置。
     *
     * @return 默认配置
     */
    private HotRankConfigDTO defaultConfig() {
        return new HotRankConfigDTO(properties.getViewWeight(), properties.getCartWeight(), properties.getOrderWeight());
    }

    /**
     * 发送补偿消息：把"因 Redis 故障未能写入"的原始事件异步发给 RocketMQ 补偿队列。
     * <p>
     * 消费端 HotDishRankCompensationListener 会调用 replay(event) 幂等重放（同一 Lua、同一事件键，
     * 不会重复加分）；asyncSend 不阻塞当前请求线程。若补偿消息也发送失败，只会累计
     * compensationFailed 指标——这是旁路统计，不影响业务主流程，代价是可能丢一次热度。
     *
     * @param event 热销榜事件
     */
    private void sendCompensation(HotRankEvent event) {
        try {
            rocketMQTemplate.asyncSend(COMPENSATION_TOPIC, JSON.toJSONString(event), new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    metrics.compensationSent();
                    log.info("热销榜补偿消息发送成功，eventId={}", event.getEventId());
                }

                @Override
                public void onException(Throwable throwable) {
                    metrics.compensationFailed();
                    log.error("热销榜补偿消息发送失败，eventId={}", event.getEventId(), throwable);
                }
            });
        } catch (Exception e) {
            metrics.compensationFailed();
            log.error("发送热销榜补偿消息异常，eventId={}", event.getEventId(), e);
        }
    }
}
