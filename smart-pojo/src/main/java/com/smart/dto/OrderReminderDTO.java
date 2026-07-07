package com.smart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(name = "OrderReminderDTO", description = "订单催单请求数据模型")
public class OrderReminderDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "订单ID不能为空")
    @Positive(message = "订单ID必须为正整数")
    @Schema(description = "订单ID")
    private Long orderId;

    @NotNull(message = "商户ID不能为空")
    @Positive(message = "商户ID必须为正整数")
    @Schema(description = "商户ID，接收催单通知的商户标识")
    private Long merchantId;
}