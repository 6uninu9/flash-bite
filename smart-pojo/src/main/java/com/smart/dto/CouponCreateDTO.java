package com.smart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家创建优惠券请求数据
 */
@Data
@Schema(name = "CouponCreateDTO", description = "商家创建优惠券请求数据")
public class CouponCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "优惠券名称不能为空")
    @Size(max = 64, message = "优惠券名称不能超过64个字符")
    private String couponName;

    @NotNull(message = "优惠券类型不能为空")
    @Min(value = 1, message = "优惠券类型错误")
    @Max(value = 2, message = "优惠券类型错误")
    private Integer couponType;

    @NotNull(message = "满减门槛不能为空")
    @DecimalMin(value = "0.00", message = "满减门槛不能为负数")
    private BigDecimal thresholdAmount;

    @NotNull(message = "优惠金额不能为空")
    @DecimalMin(value = "0.01", message = "优惠金额必须大于0")
    private BigDecimal discountAmount;

    @NotNull(message = "发放数量不能为空")
    @Positive(message = "发放数量必须大于0")
    private Integer totalStock;

    @NotNull(message = "领取开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "领取结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "有效天数不能为空")
    @Positive(message = "有效天数必须大于0")
    private Integer validDays;

    @NotNull(message = "是否秒杀券不能为空")
    @Min(value = 0, message = "是否秒杀券参数错误")
    @Max(value = 1, message = "是否秒杀券参数错误")
    private Integer isSeckill;
}
