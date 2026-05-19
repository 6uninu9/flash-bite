package com.smart.service.impl;

import com.smart.constant.CacheKeyConstants;
import com.smart.constant.MessageConstant;
import com.smart.context.BaseContext;
import com.smart.entity.Coupon;
import com.smart.entity.UserCoupon;
import com.smart.exception.BaseException;
import com.smart.mapper.CouponMapper;
import com.smart.mapper.UserCouponMapper;
import com.smart.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Slf4j
@Service
public class CouponServiceImpl implements CouponService {

    private final StringRedisTemplate stringRedisTemplate;

    private final RocketMQTemplate rocketMQTemplate;

    private final CouponMapper couponMapper;

    private final UserCouponMapper userCouponMapper;

    // 用一个静态常量空字符串，避免每次创建新对象
    private static final String EMPTY_VALUE = "";

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

    public CouponServiceImpl(StringRedisTemplate stringRedisTemplate, RocketMQTemplate rocketMQTemplate, CouponMapper couponMapper, UserCouponMapper userCouponMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rocketMQTemplate = rocketMQTemplate;
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
    }

    /**
     * 优惠券秒杀
     *
     * @param couponId 优惠券ID
     */
    @Override
    public void seckill(Long couponId) {
        // 1. 构建去重键（唯一卷） 避免用户重复抢卷
        Long userId = BaseContext.getCurrentId();
        String uk = "seckill:" + couponId + ":" + userId; // 构建去重键

        // 2. 使用setnx写入Redis缓存完成去重
        // 写入成功 则代表用户没有领取过优惠券
        // 写入失败 则代表用户已经领取过优惠券
        // 没有设置过期时间 主要因为设置过期时间需要再次查询优惠卷获取活动过期时间 影响性能
        // 可以通过定时任务定期轮询将活动过期的用户去重键删除
        if (Boolean.FALSE.equals(stringRedisTemplate.opsForValue().setIfAbsent(uk,EMPTY_VALUE))){
            log.info("用户{}已经领取过优惠券{}", userId, couponId);
            throw new BaseException(MessageConstant.USER_ALREADY_RECEIVED);
        }

        // 3. 扣减redis中的优惠券库存，完成预扣减
        // 可以使用decrement扣减原子命令 但是是不加判断的扣减 缓存中的库存显示可能会被扣为负数
        // 而这里使用Lua脚本实现判断+扣减的原子化操作
        String stockKey = CacheKeyConstants.SECKILL_COUPON_STOCK_KEY + couponId;
        // 3.1. 执行扣减脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_DEDUCT_INVENTORY_SCRIPT, // lua脚本
                Collections.singletonList(stockKey), // 缓存的key
                String.valueOf(1) // lua脚本的可变参数参数，只能接收数组
        );
        // 3.2. 判断是否扣减成功
        if (result == 0){
            log.info("活动库存不足");
            throw new BaseException(MessageConstant.COUPON_STOCK_NOT_ENOUGH);
        }

        // 4.向RocketMQ中发送消息异步落库和插入用户的优惠券抢购记录
        // 对于异步落库 除了消息队列异步解耦 还可以设置定时任务定期从redis同步到数据库
        // 而插入数据还是需要发送消息处理 要么直接串行化
        rocketMQTemplate.asyncSend("seckillTopic",couponId+"-"+userId, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("消息发送成功：{}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("消息发送失败：{}", throwable.getMessage());
            }
        });
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
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(userId)
                .couponId(couponId)
                .status(UserCoupon.STATUS_UNUSED)
                .build();
        userCouponMapper.insert(userCoupon);
    }
}
