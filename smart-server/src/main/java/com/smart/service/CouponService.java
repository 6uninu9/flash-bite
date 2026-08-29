package com.smart.service;

import com.smart.dto.CouponCreateDTO;
import com.smart.dto.CouponPageQueryDTO;
import com.smart.entity.Coupon;
import com.smart.result.PageResult;

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

    /**
     * 创建优惠券模板
     *
     * @param couponCreateDTO 创建优惠券参数
     */
    void create(CouponCreateDTO couponCreateDTO);

    /**
     * 发布优惠券活动
     *
     * @param couponId 优惠券ID
     */
    void publish(Long couponId);

    /**
     * 手动结束优惠券活动
     *
     * @param couponId 优惠券ID
     */
    void end(Long couponId);

    /**
     * 分页查询优惠券活动
     *
     * @param couponPageQueryDTO 分页查询条件
     * @return 优惠券分页结果
     */
    PageResult<Coupon> queryPage(CouponPageQueryDTO couponPageQueryDTO);

    /**
     * 领取普通优惠券
     *
     * @param couponId 优惠券ID
     */
    void claimNormal(Long couponId);
}
