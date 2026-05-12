package com.smart.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long dishId;
    private Long userId;
    private String uuid; // 用于去重
    private Long setmealId;
    private String dishFlavor;
}
