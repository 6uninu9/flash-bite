package com.smart.task;

import com.smart.constant.CacheKeyConstants;
import com.smart.entity.Coupon;
import com.smart.mapper.CouponMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 定时清理活动结束的优惠券领取的用户去重key
 */
@Component
@Slf4j
public class CleanActivityEndedCouponTakeDedupRedisTask {

    private final CouponMapper couponMapper;

    private final StringRedisTemplate stringRedisTemplate;

    public CleanActivityEndedCouponTakeDedupRedisTask(CouponMapper couponMapper, StringRedisTemplate stringRedisTemplate) {
        this.couponMapper = couponMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 每日凌晨2点清理活动结束的优惠券领取的用户去重key
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanActivityEndedCouponTakeDedupKeys() {
        log.info("开始清理活动结束的优惠券领取去重key...");

        // 1. 查询秒杀活动结束的优惠券
        List<Coupon> coupons = couponMapper.list(Coupon.builder().status(Coupon.STATUS_END).build());
        if (CollectionUtils.isEmpty(coupons)) {
            log.info("暂无过期优惠券，无需清理");
            return;
        }
        // 2. 删除去重key
        coupons.forEach(coupon -> {
            String setKey = CacheKeyConstants.SECKILL_COUPON_TAKE_DEDUP_KEY_PREFIX + coupon.getId();
            stringRedisTemplate.delete(setKey);
        });

        log.info("结束清理活动结束的优惠券领取去重key...");
    }
}
