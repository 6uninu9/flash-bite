package com.smart.controller.user;

import com.smart.entity.Coupon;
import com.smart.entity.UserCoupon;
import com.smart.result.Result;
import com.smart.service.CouponService;
import com.smart.service.UserCouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userCouponController")
@RequestMapping("/user/coupon")
@Tag(name = "C端-优惠卷接口")
public class CouponController {

    private final CouponService couponService;

    private final UserCouponService userCouponService;

    public CouponController(CouponService couponService, UserCouponService userCouponService) {
        this.couponService = couponService;
        this.userCouponService = userCouponService;
    }

    /**
     * 优惠券秒杀
     *
     * @param couponId 优惠券ID
     * @return 抢购结果
     */
    @PostMapping("/seckill")
    @Operation(
            summary = "优惠券秒杀"
    )
    public Result<String> couponSeckkill(Long couponId) {
        couponService.seckill(couponId);
        return Result.success("抢购成功，请稍后查看抢购结果...");
    }

    @GetMapping("/seckill")
    @Operation(
            summary = "查看参与秒杀活动的优惠卷"
    )
    public Result<List<Coupon>> listSeckill() {
        return Result.success(couponService.listSeckill());
    }

    /**
     * 查看用户所有的优惠券（最近三个月内）
     *
     * @return 优惠券列表
     */
    @GetMapping("/list")
    @Operation(
            summary = "查看用户所有的优惠券（最近三个月内）"
    )
    public Result<List<UserCoupon>> list() {
        return Result.success(userCouponService.list());
    }
}
