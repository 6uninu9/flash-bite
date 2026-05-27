package com.smart.controller.user;

import com.smart.result.Result;
import com.smart.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("userCouponController")
@RequestMapping("/user/coupon")
@Tag(name = "C端-优惠卷接口")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping("/seckill")
    @Operation(
            summary = "优惠券秒杀"
    )
    public Result<String> couponSeckkill(Long couponId) {
        couponService.seckill(couponId);
        return Result.success("抢购成功，请稍后查看抢购结果...");
    }
}
