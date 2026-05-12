package com.smart.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class OrderStatisticsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //待接单数量
    private Integer toBeConfirmed;

    //待派送数量
    private Integer confirmed;

    //派送中数量
    private Integer deliveryInProgress;
}
