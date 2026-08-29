package com.smart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商家优惠券分页查询条件
 */
@Data
@Schema(name = "CouponPageQueryDTO", description = "商家优惠券分页查询条件")
public class CouponPageQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Positive(message = "页码必须为正整数")
    private int page;

    @Positive(message = "每页条数必须为正整数")
    @Max(value = 100, message = "每页条数不能超过100条")
    private int pageSize;

    private String couponName;

    @Min(value = 1, message = "优惠券类型错误")
    @Max(value = 2, message = "优惠券类型错误")
    private Integer couponType;

    @Min(value = 0, message = "活动状态错误")
    @Max(value = 2, message = "活动状态错误")
    private Integer status;

    @Min(value = 0, message = "秒杀类型错误")
    @Max(value = 1, message = "秒杀类型错误")
    private Integer isSeckill;
}
