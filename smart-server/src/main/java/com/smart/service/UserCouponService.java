package com.smart.service;

import com.smart.entity.UserCoupon;

import java.util.List;

public interface UserCouponService {
    /**
     * 查询用户优惠券列表
     *
     * @return 优惠券列表
     */
    List<UserCoupon> list();
}
