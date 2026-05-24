package com.smart.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class OrdersPaymentDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //订单号
    private String orderNumber;

    //付款方式
    private Integer payMethod;

    // 商户ID，WebSocket 的路径参数，客户端的唯一标识
    private Long merchantId;
}
