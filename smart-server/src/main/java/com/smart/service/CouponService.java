package com.smart.service;

import com.smart.entity.Coupon;

import java.util.List;

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

    /**
     * 获取秒杀优惠券列表
     * @return 秒杀优惠券列表
     */
    List<Coupon> listSeckill();
}
