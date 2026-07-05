package com.smart.mapper;

import com.smart.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CouponMapper {

    /**
     * 查询所有活动中的优惠券id
     * @return 优惠券id列表
     */
    @Select("SELECT id FROM coupon")
    List<String> listAllIds();

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

    /**
     * 根据条件查询优惠券列表
     * @param coupon 优惠券
     * @return 优惠券集合
     */
    List<Coupon> list(Coupon coupon);

    /**
     * 根据id批量查询优惠券
     * @param couponIds 优惠券id集合
     * @return 优惠券集合
     */
    List<Coupon> selectBatchById(List<Long> couponIds);

    /**
     * 查询秒杀优惠券列表
     * @return 优惠券集合
     */
    @Select("SELECT id, coupon_name, coupon_type, threshold_amount, discount_amount, " +
            "total_stock, surplus_stock, start_time, end_time, valid_days, " +
            "status, create_user, update_user, create_time, update_time, is_seckill " +
            "FROM coupon WHERE is_seckill = 1 AND status = 1 ORDER BY create_time DESC")
    List<Coupon> listSeckillCoupons();
}
