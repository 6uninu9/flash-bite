package com.smart.dto;

import com.smart.entity.DishFlavor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(name = "DishDTO", description = "菜品新增/编辑数据传输模型")
public class DishDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "菜品ID（编辑时传入，新增时为空）")
    private Long id;

    //菜品名称
    @NotBlank(message = "菜品名称不能为空")
    @Size(max = 32, message = "菜品名称长度不能超过32个字符")
    @Schema(description = "菜品名称")
    private String name;

    //菜品分类id
    @NotNull(message = "菜品分类ID不能为空")
    @Positive(message = "菜品分类ID必须为正整数")
    @Schema(description = "菜品分类ID")
    private Long categoryId;

    //菜品价格
    @NotNull(message = "菜品价格不能为空")
    @DecimalMin(value = "0.00", message = "菜品价格不能小于0.00元")
    @Schema(description = "菜品价格")
    private BigDecimal price;

    //图片
    @Schema(description = "菜品图片地址")
    private String image;

    //描述信息
    @Size(max = 255, message = "菜品描述长度不能超过255个字符")
    @Schema(description = "菜品描述信息")
    private String description;

    //0 停售 1 起售
    @Min(value = 0, message = "状态值错误")
    @Max(value = 1, message = "状态值错误")
    @Schema(description = "售卖状态 0-停售 1-起售")
    private Integer status;

    //口味
    @Valid
    @Schema(description = "菜品口味列表")
    private List<DishFlavor> flavors = new ArrayList<>();

}