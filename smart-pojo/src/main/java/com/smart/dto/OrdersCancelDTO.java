package com.smart.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class OrdersCancelDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    //订单取消原因
    private String cancelReason;

}
