package com.smart.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class OrdersRejectionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    //订单拒绝原因
    private String rejectionReason;

}
