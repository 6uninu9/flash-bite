package com.smart.mapper;

import com.smart.entity.UserCoupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserCouponMapper {

    /**
     * 插入用户优惠券
     *
     * @param userCoupon 用户优惠券
     */
    @Insert("INSERT IGNORE INTO user_coupon " +
            "(user_id, coupon_id, coupon_name, coupon_type, threshold_amount, discount_amount, get_time, expire_time, is_seckill, status, create_time) " +
            "VALUES " +
            "(#{userId}, #{couponId}, #{couponName}, #{couponType}, #{thresholdAmount}, #{discountAmount}, #{getTime}, #{expireTime}, #{isSeckill}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyColumn = "id", keyProperty = "id")
    int insert(UserCoupon userCoupon);

    /**
     * 根据id查询用户优惠券
     *
     * @param userCouponId 用户优惠券id
     * @return 用户优惠券
     */
    @Select("select * from user_coupon where id = #{userCouponId}")
    UserCoupon getById(Long userCouponId);

    /**
     * 原子锁定可用且未过期的用户优惠券。
     *
     * @return 受影响行数，1 表示锁券成功
     */
    @Update("UPDATE user_coupon SET status = 3, order_id = #{orderId}, reserved_at = NOW() " +
            "WHERE id = #{userCouponId} AND user_id = #{userId} AND status = 0 " +
            "AND order_id IS NULL AND expire_time > NOW()")
    int tryReserve(Long userCouponId, Long userId, Long orderId);

    /**
     * 将当前订单锁定的用户优惠券核销。
     *
     * @return 受影响行数，1 表示核销成功
     */
    @Update("UPDATE user_coupon SET status = 1, use_time = NOW() " +
            "WHERE id = #{userCouponId} AND order_id = #{orderId} AND status = 3")
    int markUsed(Long userCouponId, Long orderId);

    /**
     * 释放当前订单锁定的用户优惠券；已过期的券进入已过期状态。
     */
    @Update("UPDATE user_coupon SET status = CASE WHEN expire_time <= NOW() THEN 2 ELSE 0 END, " +
            "order_id = NULL, reserved_at = NULL " +
            "WHERE order_id = #{orderId} AND status = 3")
    void releaseReservation(Long orderId);

    /**
     * 将可用或已锁定且到期的用户优惠券置为已过期。
     */
    @Update("UPDATE user_coupon SET status = 2, order_id = NULL, reserved_at = NULL " +
            "WHERE id = #{userCouponId} AND status IN (0, 3) AND expire_time <= NOW()")
    void markExpired(Long userCouponId);

    /**
     * 根据用户id和开始时间查询用户优惠券
     *
     * @param userId    用户id
     * @param startTime 开始时间
     * @return 用户优惠券列表
     */
    @Select("SELECT " +
            "  id, user_id, coupon_id, coupon_name, coupon_type, threshold_amount, discount_amount, " +
            "  get_time, expire_time, is_seckill, use_time, order_id, reserved_at, status, create_time " +
            "FROM user_coupon " +
            "WHERE user_id = #{userId} AND get_time >= #{startTime} " +
            "ORDER BY get_time DESC")
    List<UserCoupon> listByUserAndTime(Long userId, LocalDateTime startTime);
}
