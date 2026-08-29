package com.smart.controller.admin;

import com.smart.dto.CouponCreateDTO;
import com.smart.dto.CouponPageQueryDTO;
import com.smart.entity.Coupon;
import com.smart.result.PageResult;
import com.smart.result.Result;
import com.smart.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家端优惠券管理接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/coupon")
@Tag(name = "商家端-优惠券管理接口")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /**
     * 创建优惠券模板
     *
     * @param couponCreateDTO 创建优惠券参数
     * @return 操作结果
     */
    @PostMapping
    @Operation(summary = "创建优惠券")
    public Result<String> create(@Valid @RequestBody CouponCreateDTO couponCreateDTO) {
        log.info("创建优惠券：{}", couponCreateDTO);
        couponService.create(couponCreateDTO);
        return Result.success();
    }

    /**
     * 发布优惠券活动
     *
     * @param id 优惠券ID
     * @return 操作结果
     */
    @PutMapping("/{id}/publish")
    @Operation(summary = "发布优惠券活动")
    public Result<String> publish(@NotNull @PathVariable Long id) {
        couponService.publish(id);
        return Result.success();
    }

    /**
     * 手动结束优惠券活动
     *
     * @param id 优惠券ID
     * @return 操作结果
     */
    @PutMapping("/{id}/end")
    @Operation(summary = "手动结束优惠券活动")
    public Result<String> end(@NotNull @PathVariable Long id) {
        couponService.end(id);
        return Result.success();
    }

    /**
     * 分页查询优惠券活动和库存
     *
     * @param couponPageQueryDTO 分页查询条件
     * @return 优惠券分页结果
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询优惠券活动和库存")
    public Result<PageResult<Coupon>> page(@Valid CouponPageQueryDTO couponPageQueryDTO) {
        return Result.success(couponService.queryPage(couponPageQueryDTO));
    }
}
