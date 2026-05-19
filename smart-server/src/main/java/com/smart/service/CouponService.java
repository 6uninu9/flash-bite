package com.smart.service;

public interface CouponService {

    /**
     * 优惠券秒杀
     * @param couponId 优惠券ID
     */
    void seckill(Long couponId);

    /**
     * 扣减优惠券库存并插入用户优惠券记录
     * @param couponId 优惠券ID
     * @param userId 用户ID
     */
    void deductCouponStockAndAddUserCoupon(Long couponId, Long userId);
}
