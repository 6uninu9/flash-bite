package com.smart.listener.mq;

import com.smart.constant.CacheTimeConstant;
import com.smart.entity.UserCoupon;
import com.smart.mapper.UserCouponMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 优惠券过期延迟消息监听器
 * 消费RocketMQ延时消息，自动将优惠券状态改为 2-已过期
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = "couponTopic", // 订阅的主题
        consumerGroup = "coupon-consumer-group", // 消费者组
        consumeMode = ConsumeMode.CONCURRENTLY, // 消费模式 并发消费
        consumeThreadNumber = 32 // 并发消费线程数 处理器*2
)
public class CouponExpireListener implements RocketMQListener<String> {

    private final UserCouponMapper userCouponMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private static final String IDENTITY_KEY = "idempotent:coupon:status:";

    public CouponExpireListener(UserCouponMapper userCouponMapper, StringRedisTemplate stringRedisTemplate) {
        this.userCouponMapper = userCouponMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void onMessage(String message) {
        log.info("收到优惠券过期消息，用户优惠券ID：{}", message);

        // 1. 获取用户优惠券ID
        Long userCouponId;

        // 2. 判断消息体格式
        try {
            userCouponId = Long.valueOf(message);
        } catch (NumberFormatException e) {
            log.error("消息体格式错误，非数字类型或空字符串，无法处理。消息：{}", message, e);
            return;
        }

        try {

            // 3. 幂等性校验，避免消息重复消费
            if (Boolean.FALSE.equals(stringRedisTemplate.opsForValue().setIfAbsent(IDENTITY_KEY + userCouponId, "", CacheTimeConstant.DUPLICATE_CHECK_TTL_SECONDS, TimeUnit.SECONDS))) {
                log.warn("优惠卷过期修改消息重复");
                return;
            }

            // 4. 查询用户对应的优惠卷
            UserCoupon userCoupon = userCouponMapper.getById(userCouponId);
            // 5. 判断优惠券是否存在
            if (userCoupon == null) {
                log.error("用户优惠券不存在，用户优惠券ID：{}", userCouponId);
                return;
            }
            // 6. 判断优惠券是否已使用，使用了的优惠券不用修改
            if (userCoupon.getStatus() == UserCoupon.STATUS_USED){
                log.error("用户优惠券已使用，用户优惠券ID：{}", userCouponId);
                return;
            }
            // 7. 判断优惠券是否已过期，过期的优惠卷不用修改
            if (userCoupon.getStatus() == UserCoupon.STATUS_EXPIRE){
                log.error("用户优惠券已过期，用户优惠券ID：{}", userCouponId);
                return;
            }
            // 8. 修改优惠券状态
            userCouponMapper.updateStatusById(userCouponId, UserCoupon.STATUS_EXPIRE);
        } catch (Exception e) {
            // 报错 则删除幂等标识
            stringRedisTemplate.delete(IDENTITY_KEY + userCouponId);
            throw new RuntimeException(e);
        }
    }
}
