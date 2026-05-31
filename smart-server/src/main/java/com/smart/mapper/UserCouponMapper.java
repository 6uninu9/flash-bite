package com.smart.mapper;

import com.smart.entity.UserCoupon;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserCouponMapper {

    /**
     * 批量更新优惠券状态
     * @param couponIds 优惠券id列表
     * @param userId 用户id
     * @param status 状态
     */
    void updateBathStatus(List<Long> couponIds, Long userId, int status);

    /**
     * 插入用户优惠券
     *
     * @param userCoupon 用户优惠券
     */
    @Insert("INSERT IGNORE INTO user_coupon " +
            "(user_id, coupon_id, get_time, status, create_time) " +
            "VALUES " +
            "(#{userId}, #{couponId}, now(), #{status}, now())")
    @Options(useGeneratedKeys = true, keyColumn = "id", keyProperty = "id")
    void insert(UserCoupon userCoupon);

    /**
     * 根据订单id和用户id查询优惠券
     *
     * @param orderId 订单id
     * @param userId  用户id
     * @return 用户优惠券列表
     */
    @Select("select * from user_coupon where order_id = #{orderId} and user_id = #{userId}")
    List<UserCoupon> getByOrderIdAndUserId(Long orderId, Long userId);

    /**
     * 根据用户id和优惠券id查询优惠券
     *
     * @param userId    用户id
     * @param couponIds 优惠券id列表
     * @return 用户优惠券列表
     */
    List<UserCoupon> getByUserIdAndCouponIds(Long userId, List<Long> couponIds);

    /**
     * 根据id列表批量修改用户优惠卷的订单id
     *
     * @param userCoupons 用户优惠券列表
     */
    void updateOrderIdBathByIds(@Param("orderId") Long orderId, @Param("userCoupons") List<UserCoupon> userCoupons);

    /**
     * 根据订单id删除用户优惠卷的订单id
     *
     * @param orderId 订单id
     */
    void removeOrderIdByOrderId(Long orderId);

    /**
     * 根据id列表查询用户优惠券
     *
     * @param userCouponIds 用户优惠券id列表
     * @return 用户优惠券列表
     */
    List<UserCoupon> getByIds(List<Long> userCouponIds);

    /**
     * 根据id查询用户优惠券
     * @param userCouponId 用户优惠券id
     * @return 用户优惠券
     */
    @Select("select * from user_coupon where id = #{userCouponId}")
    UserCoupon getById(Long userCouponId);

    /**
     * 修改用户优惠券状态
     * 使用乐观锁status = 0，避免在修改时用户刚好使用优惠券，状态被覆盖的问题
     * @param userCouponId 用户优惠券id
     * @param status 状态
     */
    @Update("update user_coupon set status = #{status} where id = #{userCouponId} and status = 0")
    void updateStatusById(Long userCouponId, Integer status);
}
