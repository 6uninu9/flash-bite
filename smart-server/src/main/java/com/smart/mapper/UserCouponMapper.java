package com.smart.mapper;

import com.smart.entity.UserCoupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserCouponMapper {

    void updateBathStatus(List<Long> couponIds, Long userId, int status);

    /**
     * 插入用户优惠券
     * @param userCoupon 用户优惠券
     */
    @Insert("INSERT IGNORE INTO user_coupon " +
            "(user_id, coupon_id, get_time, status, create_time) " +
            "VALUES " +
            "(#{userId}, #{couponId}, now(), #{status}, now())")
    void insert(UserCoupon userCoupon);
}
