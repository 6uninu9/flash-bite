package com.smart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(name = "DishPageQueryDTO", description = "菜品分页查询条件")
public class DishPageQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Positive(message = "页码必须为正整数")
    @Schema(description = "当前页码")
    private int page;

    @Positive(message = "每页条数必须为正整数")
    @Max(value = 100, message = "每页条数不能超过100条")
    @Schema(description = "每页显示条数")
    private int pageSize;

    @Schema(description = "菜品名称（模糊查询）")
    private String name;

    //分类id
    @Schema(description = "分类ID")
    private Integer categoryId;

    //状态 0表示禁用 1表示启用
    @Min(value = 0, message = "状态值错误")
    @Max(value = 1, message = "状态值错误")
    @Schema(description = "状态 0-禁用 1-启用")
    private Integer status;

}