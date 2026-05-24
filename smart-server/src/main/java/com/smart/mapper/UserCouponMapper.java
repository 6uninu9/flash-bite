package com.smart.mapper;

import com.smart.entity.UserCoupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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

    /**
     * 根据订单id和用户id查询优惠券
     * @param orderId 订单id
     * @param userId 用户id
     * @return 用户优惠券列表
     */
    @Select("select * from user_coupon where order_id = #{orderId} and user_id = #{userId}")
    List<UserCoupon> getByOrderIdAndUserId(Long orderId, Long userId);
}
