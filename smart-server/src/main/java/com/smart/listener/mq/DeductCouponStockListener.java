package com.smart.listener.mq;

import com.smart.constant.CacheTimeConstant;
import com.smart.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 优惠券库存扣减 MQ 监听器
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = "seckillTopic", // 订阅的主题
        consumerGroup = "seckill-consumer-group", // 消费者组
        consumeMode = ConsumeMode.CONCURRENTLY, // 消费模式 并发消费
        consumeThreadNumber = 32 // 并发消费线程数 处理器*2
)
public class DeductCouponStockListener implements RocketMQListener<MessageExt> {

    private final StringRedisTemplate stringRedisTemplate;

    private final RedissonClient redissonClient;

    private final CouponService couponService;

    /**
     * 优惠券库存扣减消息幂等key前缀
     */
    private static final String IDENTITY_KEY = "idempotent:coupon:deduct:";

    /**
     * 扣减优惠券库存 分布式锁Key前缀
     */
    public static final String LOCK_COUPON_DEDUCT_STOCK_KEY = "lock:deductCouponStock:";

    public DeductCouponStockListener(StringRedisTemplate stringRedisTemplate, RedissonClient redissonClient, CouponService couponService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
        this.couponService = couponService;
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        // 1. 提取优惠券id和用户id
        // TODO 参数未校验
        String msg = new String(messageExt.getBody());
        String couponId = msg.split("-")[0];
        String userId = msg.split("-")[1];

        log.info("收到优惠券库存扣减的异步消息：{}", msg);

        // 2. 消息幂等性校验 避免消息重复消费
        if (Boolean.FALSE.equals(stringRedisTemplate.opsForValue().setIfAbsent(IDENTITY_KEY + couponId + ":" + userId, "", CacheTimeConstant.DUPLICATE_CHECK_TTL_SECONDS, TimeUnit.SECONDS))) {
            log.warn("优惠券库存扣减消息重复");
            return;
        }

        // 3. 创建锁
        // 为了防止并发问题 就需要避免多个线程对同一个优惠券进行扣减 所以键名应以优惠券id为准
        RLock lock = redissonClient.getLock(LOCK_COUPON_DEDUCT_STOCK_KEY + couponId);

        boolean locked = false;
        try {
            // 5. 尝试获取锁，最多等待3秒，使用看门狗机制 除非发生异常不然锁自动续期
            locked = lock.tryLock(3, TimeUnit.SECONDS);

            // 6. 如果获取锁失败
            if (!locked) {
                log.warn("获取锁超时，触发重试: couponId={}，userId={}", couponId, userId);
                // 抛出错误让RocketMQ重试
                throw new RuntimeException("获取锁失败");
            }

            // 7. 扣减优惠卷库存 完成异步落库 和 插入用户的优惠卷记录
            couponService.deductCouponStockAndAddUserCoupon(Long.valueOf(couponId), Long.valueOf(userId));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 报错 则删除幂等标识
            stringRedisTemplate.delete(IDENTITY_KEY + couponId + ":" + userId);
            throw new RuntimeException("线程中断", e);
        } catch (Exception e) { // 如果mysql取消订单释放库存异常 则不会继续往下执行
            log.error("取消订单失败", e);
            // 报错 则删除幂等标识
            stringRedisTemplate.delete(IDENTITY_KEY + couponId + ":" + userId);
            throw new RuntimeException("取消订单失败", e);
        } finally {
            // 8. 释放锁
            // 让只有当前线程才释放锁
            // 因为10秒后锁自动释放，锁给其他线程获取到，此时当前线程 locked=true，但锁已不是自己的，释放的是别人的锁。
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
