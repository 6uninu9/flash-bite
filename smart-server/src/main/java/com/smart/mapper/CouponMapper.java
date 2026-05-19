package com.smart.mapper;

import com.smart.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CouponMapper {
    /**
     * 扣减优惠券库存
     * @param couponId 优惠券id
     * @param number 扣减数量
     */
    @Update("update coupon set surplus_stock = surplus_stock - #{number} where id = #{couponId} and coupon.surplus_stock >= #{number}")
    void deductCouponStockByCouponId(String couponId, int number);

    /**
     * 根据id查询优惠券
     * @param couponId 优惠券id
     * @return 优惠券
     */
    @Select("select id, coupon_name, coupon_type, threshold_amount," +
            " discount_amount, total_stock, surplus_stock, start_time," +
            " end_time, valid_days, status, create_user, update_user," +
            " create_time, update_time, is_seckill" +
            " from coupon where id = #{couponId}")
    Coupon getById(Long couponId);

    /**
     * 根据id更新优惠券库存
     * @param couponId 优惠券id
     * @param newSurplusStock 新的库存数量
     */
    @Update("update coupon set surplus_stock = #{newSurplusStock} where id = #{couponId}")
    void deductCouponStockById(Long couponId, int newSurplusStock);


    List<Coupon> list(Coupon coupon);

    /**
     * 根据id批量查询优惠券
     * @param couponIds 优惠券id集合
     * @return 优惠券集合
     */
    List<Coupon> selectBatchById(List<Long> couponIds);
}
