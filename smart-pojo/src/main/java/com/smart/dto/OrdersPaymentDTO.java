package com.smart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(name = "OrdersPaymentDTO", description = "订单支付请求数据模型")
public class OrdersPaymentDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //订单号
    @NotBlank(message = "订单号不能为空")
    @Schema(description = "订单编号")
    private String orderNumber;

    //付款方式
    @NotNull(message = "支付方式不能为空")
    @Min(value = 1, message = "支付方式错误")
    @Max(value = 2, message = "支付方式错误")
    @Schema(description = "支付方式 1-微信支付 2-支付宝支付")
    private Integer payMethod;

    // 商户ID，WebSocket 的路径参数，客户端的唯一标识，默认为1
    @NotNull(message = "商户ID不能为空")
    @Positive(message = "商户ID必须为正整数")
    @Schema(description = "商户ID，WebSocket客户端唯一标识")
    private Long merchantId;
}