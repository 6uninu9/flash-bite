package com.smart.service.impl;

import com.smart.constant.CacheKeyConstants;
import com.smart.constant.MessageConstant;
import com.smart.context.BaseContext;
import com.smart.entity.Coupon;
import com.smart.entity.UserCoupon;
import com.smart.exception.BaseException;
import com.smart.exception.SystemException;
import com.smart.mapper.CouponMapper;
import com.smart.mapper.UserCouponMapper;
import com.smart.service.BloomCacheService;
import com.smart.service.BloomFilterDataService;
import com.smart.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class CouponServiceImpl implements CouponService, BloomFilterDataService {

    private final StringRedisTemplate stringRedisTemplate;

    private final RocketMQTemplate rocketMQTemplate;

    private final CouponMapper couponMapper;

    private final UserCouponMapper userCouponMapper;

    private final BloomCacheService bloomCacheService;

    @Qualifier("couponBloomFilter")
    private final RBloomFilter<String> couponBloomFilter;

    // 声明脚本
    private static final DefaultRedisScript<Long> SECKILL_DEDUCT_INVENTORY_SCRIPT;

    // 初始化脚本
    static {
        SECKILL_DEDUCT_INVENTORY_SCRIPT = new DefaultRedisScript<>();
        // 指定脚本位置
        SECKILL_DEDUCT_INVENTORY_SCRIPT.setLocation(new ClassPathResource("seckillDeductInventory.lua"));
        // 指定脚本返回值类型
        SECKILL_DEDUCT_INVENTORY_SCRIPT.setResultType(Long.class);
    }

    private static final String COUPON_SECKILL = "优惠卷秒杀";

    public CouponServiceImpl(StringRedisTemplate stringRedisTemplate, RocketMQTemplate rocketMQTemplate, CouponMapper couponMapper, UserCouponMapper userCouponMapper, BloomCacheService bloomCacheService, RBloomFilter<String> couponBloomFilter) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rocketMQTemplate = rocketMQTemplate;
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.bloomCacheService = bloomCacheService;
        this.couponBloomFilter = couponBloomFilter;
    }

    /**
     * 优惠券秒杀
     *
     * @param couponId 优惠券ID
     */
    @Override
    public void seckill(Long couponId) {

        // 1. 查询布隆过滤器是否存在该优惠卷id，避免缓存穿透
        if (bloomCacheService.contains(couponBloomFilter, couponId.toString())) {
            // 1.1. 不存在，直接结束
            throw new BaseException(MessageConstant.COUPON_NOT_EXIST);
        }

        // 2. 构建去重键（唯一键） 避免用户重复抢卷
        Long userId = BaseContext.getCurrentId();

        try {
            // 3. 判断优惠卷是否在活动时间段内
            List<Object> times = stringRedisTemplate.opsForHash().multiGet(
                    CacheKeyConstants.SECKILL_COUPON_STATUS_KEY + couponId,
                    Arrays.asList("start_time", "end_time")
            );
            if (times.getFirst() == null) {
                throw new BaseException(COUPON_SECKILL + MessageConstant.ACTIVITY_NOT_EXIST);
            }
            long startTime = Long.parseLong(times.get(0).toString());
            long endTime = Long.parseLong(times.get(1).toString());
            long now = System.currentTimeMillis();
            if (now < startTime) {
                throw new BaseException(COUPON_SECKILL + MessageConstant.ACTIVITY_NOT_START);
            }
            if (now > endTime) {
                throw new BaseException(COUPON_SECKILL + MessageConstant.ACTIVITY_ENDED);
            }


            // 4. 使用redis的Set集合存储用户id完成去重
            // 不使用setnx写入Redis缓存完成去重原因在于：
            //  - 若用 SETNX 为每个用户+优惠券组合创建一个独立的Key，会导致大量零散Key（如 "seckill:couponId:123:456"）
            //    使用定时任务轮询删除这些去重键时 需要扫描大量的去重有seckill:couponId:前缀的key 损耗性能的同时会造成堵塞
            // 而使用Set集合的原因在于：
            //  - 每个优惠券只对应一个 Set Key（如 "dedup:seckill:coupon:123"），清理时只需直接删除该Key即可，O(1)操作
            //  - Set 内部自动去重，判断用户是否已领取只需执行 sadd 命令，根据返回值（0表示已存在）即可快速拒绝重复领取
            // 4.1. 设置Set集合的key和member
            String setKey = CacheKeyConstants.SECKILL_COUPON_TAKE_DEDUP_KEY_PREFIX + couponId;   // 例如 "seckill:coupon:123"
            String member = String.valueOf(userId);         // 成员是 userId
            // 4.2. 将用户id写入Set集合
            Long addResult = stringRedisTemplate.opsForSet().add(setKey, member);
            // 4.3. 判断是否写入成功，如果失败则代表用户领取过该优惠券
            if (addResult == null || addResult == 0) {
                throw new BaseException(MessageConstant.USER_ALREADY_RECEIVED);
            }

            // 5. 扣减redis中的优惠券库存，完成预扣减
            // 可以使用decrement扣减原子命令 但是是不加判断的扣减 缓存中的库存显示可能会被扣为负数
            // 而这里使用Lua脚本实现判断+扣减的原子化操作
            String stockKey = CacheKeyConstants.SECKILL_COUPON_STOCK_KEY + couponId;
            // 5.1. 执行扣减脚本
            Long result = stringRedisTemplate.execute(
                    SECKILL_DEDUCT_INVENTORY_SCRIPT, // lua脚本
                    Collections.singletonList(stockKey), // 缓存的key
                    String.valueOf(1) // lua脚本的可变参数参数，只能接收数组
            );
            // 5.2. 判断是否扣减成功
            if (result == 0) {
                throw new BaseException(MessageConstant.COUPON_STOCK_NOT_ENOUGH);
            }
        } catch (BaseException e) {
            // 业务异常直接抛出
            throw e;
        } catch (DataAccessException e) {
            // 降级策略：Redis 连接异常、超时、宕机时，触发快速失败（熔断），保护 DB
            throw new SystemException(MessageConstant.ACTIVITY_TOO_BUSY, e);
        } catch (Exception e) {
            throw new SystemException(MessageConstant.SYSTEM_BUSY, e);
        }

        // 6.向RocketMQ中发送消息异步落库和插入用户的优惠券抢购记录
        // 对于异步落库 除了消息队列异步解耦 还可以设置定时任务定期从redis同步到数据库
        // 而插入数据还是需要发送消息处理 要么直接串行化
        try {
            rocketMQTemplate.asyncSend("seckillTopic", couponId + "-" + userId, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("秒杀MQ消息发送成功：{}", sendResult);
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("秒杀MQ消息发送失败，需人工或定时任务补偿：{}", throwable.getMessage());
                }
            });
        } catch (Exception e) {
            throw new SystemException(MessageConstant.SYSTEM_BUSY, e);
        }
    }

    /**
     * 扣减优惠券库存并插入用户优惠券记录
     *
     * @param couponId 优惠券ID
     * @param userId   用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductCouponStockAndAddUserCoupon(Long couponId, Long userId) {
        // 1. 查询优惠卷库存
        Coupon coupon = couponMapper.getById(couponId);

        // 2. 扣减库存
        couponMapper.deductCouponStockById(couponId, coupon.getSurplusStock() - 1);

        // 3. 插入用户优惠券记录
        LocalDateTime getTime = LocalDateTime.now();
        LocalDateTime expireTime = getTime.plusDays(coupon.getValidDays());

        UserCoupon userCoupon = UserCoupon.builder()
                .userId(userId)
                .couponId(couponId)
                .couponName(coupon.getCouponName())
                .couponType(coupon.getCouponType())
                .thresholdAmount(coupon.getThresholdAmount())
                .discountAmount(coupon.getDiscountAmount())
                .isSeckill(coupon.getIsSeckill())
                .getTime(getTime)
                .expireTime(expireTime)
                .status(UserCoupon.STATUS_UNUSED)
                .build();
        userCouponMapper.insert(userCoupon);

        // 4. 创建自动修改过期优惠卷状态的延时消息
        String msg = userCoupon.getId().toString();
        Message rocketMsg = new Message(
                "couponTopic",
                msg.getBytes(StandardCharsets.UTF_8)
        );
        // 开源版 RocketMQ 5.X的自定义延时时间默认最大为3天，可以通过修改配置文件修改时间
        // 正式环境：直接使用过期时间戳作为投递时间，逻辑更精准直观
        long deliverTimestamp = Timestamp.valueOf(expireTime).getTime();
//        rocketMsg.setDeliverTimeMs(deliverTimestamp);

        // 测试环境：1秒后触发，注释正式环境配置后启用
         rocketMsg.setDeliverTimeMs(System.currentTimeMillis() + 1000L);

        // 5. 发送延时消息
        try {
            rocketMQTemplate.getProducer().send(rocketMsg, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("延时消息发送成功：{}", sendResult);
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("延时消息发送失败：{}", throwable.getMessage());
                }
            });
        } catch (MQClientException | RemotingException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取秒杀优惠券列表
     *
     * @return 秒杀优惠券列表
     */
    @Override
    public List<Coupon> listSeckill() {
        return couponMapper.listSeckillCoupons();
    }

    /**
     * 获取所有优惠券ID
     *
     * @return 所有优惠券ID
     */
    @Override
    public List<String> getKey() {
        return couponMapper.listAllIds();
    }
}
