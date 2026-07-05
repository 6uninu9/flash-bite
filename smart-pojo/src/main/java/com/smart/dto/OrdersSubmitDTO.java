package com.smart.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(name = "OrdersSubmitDTO", description = "用户提交订单时传递的数据模型")
public class OrdersSubmitDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //地址簿id
    @NotNull(message = "地址簿ID不能为空")
    @Schema(description = "地址簿ID")
    private Long addressBookId;

    //付款方式
    @NotNull(message = "支付方式不能为空")
    @Min(value = 1, message = "支付方式错误")
    @Max(value = 2, message = "支付方式错误")
    @Schema(description = "支付方式 1-微信支付 2-支付宝支付")
    private Integer payMethod;

    //备注
    @Schema(description = "订单备注")
    private String remark;

    //预计送达时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "预计送达时间", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime estimatedDeliveryTime;

    //配送状态  1立即送出  0选择具体时间
    @NotNull(message = "配送状态不能为空")
    @Min(value = 0, message = "配送状态错误")
    @Max(value = 1, message = "配送状态错误")
    @Schema(description = "配送状态 1-立即送出 0-选择具体时间")
    private Integer deliveryStatus;

    //餐具数量
    @PositiveOrZero(message = "餐具数量不能为负数")
    @Schema(description = "餐具数量")
    private Integer tablewareNumber;

    //餐具数量状态  1按餐量提供  0选择具体数量
    @Min(value = 0, message = "餐具状态错误")
    @Max(value = 1, message = "餐具状态错误")
    @Schema(description = "餐具数量状态 1-按餐量提供 0-选择具体数量")
    private Integer tablewareStatus;

    //打包费
    @PositiveOrZero(message = "打包费不能为负数")
    @Schema(description = "打包费（单位：分）")
    private Integer packAmount;

    //总金额
    //注意：这里的amount我们后端会重新计算，所以只做基础校验
    @DecimalMin(value = "0.0", message = "订单金额不能小于0.0元")
    @Schema(description = "订单总金额")
    private BigDecimal amount;

    //用户优惠卷id集合（用户可能不止选了一张优惠卷）
    @Schema(description = "用户优惠券ID集合")
    private List<Long> userCouponIds;
}