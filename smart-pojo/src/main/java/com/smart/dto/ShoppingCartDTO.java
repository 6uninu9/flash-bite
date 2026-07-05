package com.smart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(name = "ShoppingCartDTO", description = "购物车操作数据模型")
public class ShoppingCartDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "菜品ID不能为空")
    @Positive(message = "菜品ID必须为正整数")
    @Schema(description = "菜品ID")
    private Long dishId;

    @Schema(description = "菜品口味规格")
    private String dishFlavor;

}